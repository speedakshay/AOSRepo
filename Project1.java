/*********************************************************************
Author  - Akshay Patekar					     *
Descrition - This program is the implementation of Skeen's algorithm *
	     for totally ordered multicast system.		     *
	     This program needs to be running on all the machines     *
	     in the cluster					     *	
Input - config.txt - contains mapping of node number 		     *
	       	     and IpAddresses of the machines in the cluster  *
	input.txt - contains messages and multicast group to 	     *
		    whom messages will be delivered		     *
Output - Messages will be delivered to machines and displayed 	     *
         on the console of respective machine. 			     *
**********************************************************************/

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.*;
import java.net.*;
import java.nio.*;

import com.sun.nio.sctp.*;

import java.io.*;
import java.lang.Object;


// use   RemoteServerClientMap to data transfer as it contains mapping of serveraddress- address with ephemeral port-local client stub for that server 
class Pair {
    String Servervalue;
    String Clientvalue;
    SctpChannel TargetChannelvalue;
  
    public Pair(String Server,String Client,SctpChannel TargetChannel)
    {
    this.Servervalue=Server;
    this.Clientvalue=Client;
    this.TargetChannelvalue=TargetChannel;
    }
}



class Message_Structure implements Serializable  
{
static int MAX_BUF_SIZE=1000;
int Message_id;
String Msg_type,Destination,Source,Message;
int TS;
boolean isDeliverable;
public Message_Structure(int Message_id,String Msg_type,String Source,String Destination,String Message,int TS,boolean isDeliverable)
	{
	 this.Message_id=Message_id;
	 this.Msg_type=Msg_type; 
	 this.Destination=Destination;
	 this.Source=Source;
	 this.Message=Message;
	 this.TS=TS;
	 this.isDeliverable=isDeliverable;
	}

public static byte[] serialize(Object obj) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ObjectOutputStream os = new ObjectOutputStream(out);
    os.writeObject(obj);
    return out.toByteArray();
}
public static Object deserialize(ByteBuffer parabyteBuffer) throws IOException, ClassNotFoundException {
	parabyteBuffer.position(0);
	parabyteBuffer.limit(MAX_BUF_SIZE);
	byte[] bufArr = new byte[parabyteBuffer.remaining()];
	parabyteBuffer.get(bufArr);
	
	ByteArrayInputStream in = new ByteArrayInputStream(bufArr);
    ObjectInputStream is = new ObjectInputStream(in);
    return is.readObject();
}
}


class ClientClass extends Thread{
	int MAX_BUF_SIZE=1000;
	public SctpChannel client;
	PriorityQueue<String> Message_Q;
	ByteBuffer byteBuffer = ByteBuffer.allocate( MAX_BUF_SIZE);
	MessageInfo messageInfo;
	int Msg_count;
	public SocketAddress ServerSocketAddress;
	
	
	public static String Get_IPV4(Set<SocketAddress> Address_List)
	{
		String[] a=null;
		SocketAddress [] IPList=null;
		
		IPList=Address_List.toArray(new SocketAddress[0]);
		
		String IPV4_REGEX = "/\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{1,5}";
		
		for(SocketAddress t : IPList)
		{
			
			if(t.toString().matches(IPV4_REGEX)  & !t.toString().contains("127.0.0.1"))
			return t.toString();	
		}
		return "IPV4 not found";
	}
	
		
	public  ClientClass(String ServerIPAddr,int ServerPortNum) // ok 
	{
		
		Message_Q=new PriorityQueue<String>();
		ServerSocketAddress=new InetSocketAddress(ServerIPAddr,ServerPortNum);
		int attempts=0;
		try{
	        client = SctpChannel.open();
	        while(!client.connect(ServerSocketAddress))
			{
	    	   attempts++;
	    	   if(attempts>5)
				{
					System.out.println("Could not connect to "+ServerSocketAddress.toString() +" after "+attempts+" attempts");
					return;
				}
			}
	        if(attempts<=5)
	        {
	    	 System.out.println("Client "+Get_IPV4(client.getAllLocalAddresses())+" is connected to server:"+Get_IPV4(client.getRemoteAddresses()));
	        }
		}catch(IOException ioe){}
 	}

}




/////////////////////////////////////////////Server class starts here//////////////////////////////////////////////////////////////////////////////

public class Project1 extends Thread {
	static int MAX_BUF_SIZE=1000;
	public static String OUT_PATH=System.getProperty("user.home")+"/AOS/outputdir/";
	String OutputFileName;
	Writer writer;
    static int totalNodeCount;
	String ServerIP;
	int ServerPort;

	SctpServerChannel ss;
    HashSet<SctpChannel> Clients;
	SctpChannel client;

	ArrayList<Message_Structure> Received_Messages_Q;
	ArrayList<Message_Structure> Sent_Messages_Q;
	int Myclock;
	int Send_Message_id;
	
	static String[] NetworkIPList;//={"10.0.2.15:5000","10.0.2.15:5001","10.0.2.15:5002"};
	List<Pair> RemoteServerClientMap;
	static ArrayList<Pair> IPAddressNodeMap;
	
	public static String Get_IPV4(Set<SocketAddress> Address_List)//OK
	{
		
		SocketAddress [] IPList=null;
		IPList=Address_List.toArray(new SocketAddress[0]);
		String IPV4_REGEX = "/\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{1,5}";
		for(SocketAddress t : IPList)
		{
			if(t.toString().matches(IPV4_REGEX) & !t.toString().contains("127.0.0.1"))
			return t.toString();	
		}
		return "IPV4 not found";
	}
	
	public static void Read_Config()
	{
		//read config.txt
		BufferedReader br = null;
		Pair tmpMap;
		String sCurrentLine;
		totalNodeCount=0;
		IPAddressNodeMap=new ArrayList<Pair>();
		int i=0;
		String[] Map;
		
				try {
					
					br = new BufferedReader(new FileReader(System.getProperty("user.home")+"/AOS/config.txt"));
						while ((sCurrentLine = br.readLine()) != null) 
						{
							Map=sCurrentLine.split(" ");
							tmpMap=new Pair(Map[0],Map[1],null);
							IPAddressNodeMap.add(tmpMap);
							totalNodeCount++;
						}
					} catch (Exception e) {e.printStackTrace();} 
					  finally {	try {if (br != null)br.close();} catch (IOException ex) {ex.printStackTrace();}}
					NetworkIPList=new String[totalNodeCount];
					i=0;
					for (Pair t : IPAddressNodeMap)
					{
						NetworkIPList[i]=t.Clientvalue;
						i++;
					}
	}
	
	public Project1(int ServerPort)  //ok 
	{
		
		
		this.ServerPort=ServerPort;
		Received_Messages_Q=new ArrayList<Message_Structure>();
		Sent_Messages_Q=new ArrayList<Message_Structure>();
		RemoteServerClientMap=new ArrayList<Pair>();
		
		this.Myclock=0;
		this.Send_Message_id=0;
		try {
			this.OutputFileName="DeliveryFileFor_"+InetAddress.getLocalHost().getHostName()+Integer.toString(ServerPort);
		} catch (UnknownHostException e) {}
		File fout=new File(OUT_PATH,OutputFileName);
		
		try{if (fout.exists()) fout.delete();
		fout.createNewFile();}catch(IOException ioe){ioe.printStackTrace();System.out.println("Problem creating file");}
		
		
 	}
	
	public static String Get_IP_from_nodenumber(String nodenumber)
	{
		for (Pair t : IPAddressNodeMap)
		{
			if(t.Servervalue.equals(nodenumber))
				return t.Clientvalue;
		}
		return "Mapping not found";
	}
	
	
	public void printMap()//OK
	{
		try{
		System.out.println("For Server:"+Get_IPV4(ss.getAllLocalAddresses()));
		System.out.println("RemoteServer		CorrespondingClient");
		for (Pair p :RemoteServerClientMap)
		{
			System.out.println(p.Servervalue+"	"+p.Clientvalue);
		}
		}catch(IOException ioe){}
	}
	
	public   Pair Get_Index_of_ServerAddress(List<Pair> PairList,String Address)
	{
		Pair x=null;
		for (Pair tmpPair : PairList)
			if (tmpPair.Servervalue.contains(Address))
				return tmpPair;
		return x;
	}
	
	public void StartServer() // ok 
	{
		Clients=new HashSet<SctpChannel>();
		try
		{
			ss=SctpServerChannel.open();
			ss.bind(new InetSocketAddress(this.ServerPort));	
			System.out.println("Server started and listening on address:"+Get_IPV4(ss.getAllLocalAddresses()));
			
		while(true)
		{
			client=ss.accept();
			client.configureBlocking(false);
			Clients.add(client);
			System.out.println("Server "+Get_IPV4(client.getAllLocalAddresses())+" is connected to client:"+Get_IPV4(client.getRemoteAddresses()));
		}
		} catch (IOException e) {/*e.printStackTrace();*/} //catch(InterruptedException ie) {System.out.println("Exception due to sleep");}
	}
	
	public  void Connect_to_Servers() //ok
	{
		String ServerIPtmp;
		int ServerPorttmp;
		ClientClass tmpsocket;
		//Client_List=new HashSet<ClientClass>();
		int i;
		String[] ValidServers=new String[NetworkIPList.length-1];
		i=0;
		try{
		for(String NodeAddress : NetworkIPList)
			if(!NodeAddress.contains(":"+ServerPort))
				ValidServers[i++]=NodeAddress;
		
				for (i=0;i<ValidServers.length;i++) 
				{
					ServerIPtmp=ValidServers[i].split(":")[0];
					ServerPorttmp=Integer.parseInt(ValidServers[i].split(":")[1]);
					tmpsocket=new ClientClass(ServerIPtmp,ServerPorttmp);
				//	Client_List.add(tmpsocket);
					tmpsocket.client.configureBlocking(false);
					RemoteServerClientMap.add(new Pair(Get_IPV4(tmpsocket.client.getRemoteAddresses()),Get_IPV4(tmpsocket.client.getAllLocalAddresses()),tmpsocket.client));
				}
		}catch(IOException ioe){}
	}
	
	public   boolean Check_if_present(String[] StringArray,String search,int startindex,int endindex) // ok
	{
		int i;
		for(i=startindex;i<=endindex;i++)
		{
			if(StringArray[i].contains(search))
				return true;
		}
		return false;
	}
	
	public   boolean Is_target_present(Set<SocketAddress> Address_List, String [] target)
	{
		String tmpAddr;
		for(SocketAddress SA : Address_List)
		{
			tmpAddr=SA.toString().substring(1).split(":")[0];
		  if(Check_if_present(target,tmpAddr,0,target.length-1))
			  return true;
		}
			return false;
	}
	
	public   boolean Update_Sent_Q_by_Proposal(String TargetAddr,Message_Structure Tmp_proposal)
	{
		
		System.out.println("Updating Sent_Q with proposals from "+TargetAddr+" for Msg_id: "+Tmp_proposal.Message_id);
		ArrayList<Message_Structure> tmpMStruct=new ArrayList<Message_Structure>(this.Sent_Messages_Q);
		for(Message_Structure s : tmpMStruct)
		{
			if(s.Destination.equals("/"+TargetAddr) & s.Message_id==Tmp_proposal.Message_id)
			{
				synchronized(this.Sent_Messages_Q){ this.Sent_Messages_Q.remove(s);
				this.Sent_Messages_Q.add(Tmp_proposal);}
				return true;
			}
		}
		return false;
	}
	
	public   int Retrieve_Proposal_Max(int Msg_id)
	{
		int a=0;
		ArrayList<Message_Structure> tmpMStruct=new ArrayList<Message_Structure>(this.Sent_Messages_Q);
		try{
		System.out.println("Removing from Sent_Q of server "+Get_IPV4(ss.getAllLocalAddresses()));
		}catch(Exception e){}
		for(Message_Structure s : tmpMStruct)
			{
				if( s.Message_id==Msg_id )
				{
					if(s.TS>a)	
						a=s.TS;
					synchronized(this.Sent_Messages_Q)
					{if(this.Sent_Messages_Q.remove(s)==true) System.out.println("Removed successfully from Sent_Q for Msgid:"+Msg_id);}
				}
			}
		return a;
	}
	
	public   boolean Update_Receive_Q_by_Counterproposal(Message_Structure Tmp_proposal)
	{
		System.out.println("Updating Receive_Q with counterproposals from "+Tmp_proposal.Source+" for Msg_id: "+Tmp_proposal.Message_id);
		ArrayList<Message_Structure> tmpMStruct=new ArrayList<Message_Structure>(this.Received_Messages_Q);
		for(Message_Structure s : tmpMStruct)
		{
			if(s.Source.equals(Tmp_proposal.Source) & s.Message_id==Tmp_proposal.Message_id)
			{
				Tmp_proposal.TS=Integer.parseInt(Tmp_proposal.Message);
				Tmp_proposal.Message=s.Message;
				synchronized(this.Received_Messages_Q) {this.Received_Messages_Q.remove(s);
				this.Received_Messages_Q.add(Tmp_proposal);}
				return true;
			}
		}
		return false;
	}
	
	public  Message_Structure Retrieve_Counterproposal_Min()
	{
		Message_Structure Deliverable_Msg=null;
		int a=99999;
		try{
			System.out.println("Removing from Receive_Q of client "+Get_IPV4(ss.getAllLocalAddresses()));
			}catch(Exception e){}
		
		for(Message_Structure s : this.Received_Messages_Q)
		{if(s.isDeliverable==true)
			{if(s.TS<a)	a=s.TS;
			 Deliverable_Msg=s;
			}
		}
		synchronized(this.Received_Messages_Q){this.Received_Messages_Q.remove(Deliverable_Msg);}
	return Deliverable_Msg;
	}
	
	public  void m_send(String[] TargetAddresses,String Msg)//  done multicasting and Skeen's algo
	{
		//send to target addresses after finding the exact client channel on local server for given target server ipaddresses
		SctpChannel tmpClient;
		Pair tmpPair;
		String TargetServer;
		int tmp_Send_Message_id;
		MessageInfo messageInfo;
		ByteBuffer byteBuffer = ByteBuffer.allocate(MAX_BUF_SIZE);
		
		synchronized(this){this.Myclock++;
		this.Send_Message_id++;
		tmp_Send_Message_id=this.Send_Message_id;
		System.out.println("Multicasting message:"+Msg+" at time "+this.Myclock);}
		 
		 for (int i=0;i<TargetAddresses.length;i++)
		 {
			 TargetServer=TargetAddresses[i];
			 if((tmpPair=Get_Index_of_ServerAddress(RemoteServerClientMap, TargetServer))!=null)   
			 {
				 try
					{
					 byteBuffer = ByteBuffer.allocate(MAX_BUF_SIZE);
					 tmpClient=tmpPair.TargetChannelvalue;
					 
					 Message_Structure Send_Message_Object = new Message_Structure(tmp_Send_Message_id,"type:message", 
							 								Get_IPV4(ss.getAllLocalAddresses()),
							 								Get_IPV4(tmpClient.getRemoteAddresses()), 
							 								Msg, this.Myclock,false);
					 System.out.println(Get_IPV4(tmpClient.getAllLocalAddresses()) +" Sending message:"+Msg+" to "+Get_IPV4(tmpClient.getRemoteAddresses()));
					 messageInfo = MessageInfo.createOutgoing(null,0);
					 byteBuffer.put(Message_Structure.serialize(Send_Message_Object));
					 byteBuffer.flip();
					 tmpClient.send(byteBuffer,messageInfo);
					 byteBuffer.clear();
					 synchronized(this){this.Sent_Messages_Q.add(Send_Message_Object);}
					}catch(IOException ioe){System.out.println("Problem in send");ioe.printStackTrace();}
			 }
			 else
			 {
				 System.out.println("Problem locating target channel");
			 }
		 }
		 
		 ///////////////////////// To receive Proposal ///////////////////////////////////////
		 int Target_Count=TargetAddresses.length;
		 Message_Structure Receive_Proposal_Object;
		 while(Target_Count>0)
		 {
			 try{
			 System.out.println(Get_IPV4(ss.getAllLocalAddresses())+"Receiving proposals");
			 }catch(IOException ioe){}
			 
			 for (int i=0;i<TargetAddresses.length;i++)
			 {
				 TargetServer=TargetAddresses[i];
				 if((tmpPair=Get_Index_of_ServerAddress(RemoteServerClientMap, TargetServer))!=null)   
				 {
					 tmpClient=tmpPair.TargetChannelvalue;
					 try
						{
						    System.out.println("Receiving proposal from: "+Get_IPV4(tmpClient.getRemoteAddresses()));
						 
						 	byteBuffer = ByteBuffer.allocate(MAX_BUF_SIZE);	
							byteBuffer.rewind();
							byteBuffer.clear();
							messageInfo = tmpClient.receive(byteBuffer,null,null);
							Receive_Proposal_Object=(Message_Structure)Message_Structure.deserialize(byteBuffer);
							synchronized(this){this.Myclock=Math.max(this.Myclock,Receive_Proposal_Object.TS)+1;}
							Target_Count--;
						if(Receive_Proposal_Object.Msg_type.equals("type:proposal"))
						{
							if(this.Update_Sent_Q_by_Proposal(TargetServer, Receive_Proposal_Object)==true) System.out.println("Sent_Q updated successfully!");
						}
						else
							{
								System.out.println("Received message/counterproposal instead of proposalXX");
							}
						}catch(IOException ioe){System.out.println("Nothing received");/*ioe.printStackTrace();*/}catch(Exception e){System.out.println("Problem in Proposal reception");} 
				 }
				 else
				 {
					 System.out.println("Problem locating target channel");
				 }
			 }
			 
			 if(Target_Count>0)
			 {
			 System.out.println("Not received all proposals yet. Rechecking in 2 secs.......");
			 try {
			      Thread.sleep(2000);
			    } catch (InterruptedException ie) {    	}
			 }
		 }
		 
		 System.out.println("End of Receiving proposals");
		 //////////////////////// Choose Max of proposals and remove from Sent_Q////////////////////////////////////
		 int Max_TS=0;
		
			   Max_TS=Retrieve_Proposal_Max(tmp_Send_Message_id);
		
		 
		 /////////////////////// to Send Counter proposals and remove from Sent_Q ///////////////////////////////////
		 
		 this.Myclock++;
		 System.out.println("Multicasting counterproposal with timestamp:"+Max_TS);
			
		 for (int i=0;i<TargetAddresses.length;i++)
		 {
			 TargetServer=TargetAddresses[i];
			 if((tmpPair=Get_Index_of_ServerAddress(RemoteServerClientMap, TargetServer))!=null)   
			 {
				 try
					{
					 byteBuffer = ByteBuffer.allocate(MAX_BUF_SIZE);
					 tmpClient=tmpPair.TargetChannelvalue;
					 
					 Message_Structure Send_Message_Object = new Message_Structure(tmp_Send_Message_id,"type:counterproposal", 
							 								Get_IPV4(ss.getAllLocalAddresses()),
							 								Get_IPV4(tmpClient.getRemoteAddresses()), 
							 								Integer.toString(Max_TS), this.Myclock,true);
					 System.out.println(Get_IPV4(tmpClient.getAllLocalAddresses()) +" Sending :"+Msg+" to "+Get_IPV4(tmpClient.getRemoteAddresses()));
					 messageInfo = MessageInfo.createOutgoing(null,0);
					 byteBuffer.put(Message_Structure.serialize(Send_Message_Object));
					 byteBuffer.flip();
					 tmpClient.send(byteBuffer,messageInfo);
					 byteBuffer.clear();
					 System.out.println(Get_IPV4(tmpClient.getAllLocalAddresses()) +" Sent :"+Msg+" to "+Get_IPV4(tmpClient.getRemoteAddresses()));
					}catch(IOException ioe){System.out.println("Problem in send");ioe.printStackTrace();}
			 }
			 else
			 {
				 System.out.println("Problem locating target channel");
			 }
		 }
	}
	
	public  void m_receive() 
	{
		int i;
		String tmp;
		MessageInfo messageInfo;
		ByteBuffer byteBuffer = ByteBuffer.allocate(MAX_BUF_SIZE);
		Message_Structure Receive_Message_Object = null;
		Message_Structure Send_proposal_Object=null;
		Iterator<SctpChannel> CCIterator;
		SctpChannel tmpClient;
		boolean Delivery_complete=false;

		while( Delivery_complete==false )
		{	
			CCIterator=Clients.iterator();
			
		for(i=0;i<Clients.size();i++)
		  {
			synchronized(Clients){
				tmpClient=CCIterator.next();
				}
			if(tmpClient.isOpen())
			{
				try
				{
			
				byteBuffer = ByteBuffer.allocate(MAX_BUF_SIZE);	
				byteBuffer.rewind();
				byteBuffer.clear();
				messageInfo = tmpClient.receive(byteBuffer,null,null);
				Receive_Message_Object=(Message_Structure)Message_Structure.deserialize(byteBuffer);
				synchronized(this){this.Myclock=Math.max(this.Myclock,Receive_Message_Object.TS)+1;}
				tmp=Receive_Message_Object.Message;
				System.out.println("Received from: "+Get_IPV4(tmpClient.getRemoteAddresses())+" message: "+tmp+" isDeliverble:"+Receive_Message_Object.isDeliverable+" type is:"+Receive_Message_Object.Msg_type);
				System.out.println("Channel status is :"+tmpClient.isOpen());
				
				if(Receive_Message_Object.Msg_type.equals("type:message"))
				{
					synchronized(this){Received_Messages_Q.add(Receive_Message_Object);
					this.Myclock++;}
					try
					{
						byteBuffer = ByteBuffer.allocate(MAX_BUF_SIZE);
						Send_proposal_Object = new Message_Structure(Receive_Message_Object.Message_id,"type:proposal", 
						 								Receive_Message_Object.Source,
						 								Receive_Message_Object.Destination, 
						 								Receive_Message_Object.Message, this.Myclock,false);
						System.out.println(Get_IPV4(tmpClient.getAllLocalAddresses()) +" Sending proposal timestamp:"+this.Myclock+" to "+Get_IPV4(tmpClient.getRemoteAddresses())+" for Msg_id:"+Send_proposal_Object.Message_id);
						messageInfo = MessageInfo.createOutgoing(null,0);
						byteBuffer.put(Message_Structure.serialize(Send_proposal_Object));
						byteBuffer.flip();
						tmpClient.send(byteBuffer,messageInfo);
						byteBuffer.clear();
					}catch(IOException ioe){System.out.println("Problem in send");ioe.printStackTrace();}
				}
			
				if(Receive_Message_Object.Msg_type.equals("type:counterproposal"))
				{
					if(this.Update_Receive_Q_by_Counterproposal(Receive_Message_Object)==true)
						System.out.println("Receive_Q updated successfully!");
					else System.out.println("Problem updating Receive_Q!");
					
			/////////////////////////////Delivering msg with smallest timestamp from Receive_Q//////////////////
					
					Message_Structure Deliver=null;
					if((Deliver=Retrieve_Counterproposal_Min())!=null)
					{
			synchronized(this){
				System.out.println("Delivering from:"+Deliver.Source+" to:"+
				Deliver.Destination+" message ---------------->"+Deliver.Message+" at time:"+this.Myclock);
						try	{
						 	writer = new BufferedWriter(new OutputStreamWriter(
						        new FileOutputStream(this.OUT_PATH+this.OutputFileName, true), "UTF-8"));
						 	writer.append("\nReceived from:"+Deliver.Source+" message--->"+Deliver.Message);
							writer.close();
							}catch(Exception ue){ue.printStackTrace();} 
							
						}
					Delivery_complete=true;
					}
					
				}
				
				if(Receive_Message_Object.Msg_type.equals("type:proposal"))
				{
					System.out.println("Received proposal by mistake yy");
				}
				
				System.out.println("OK");
				}catch(IOException ioe){/*System.out.println("Nothing received");*/}
				catch(Exception e){System.out.println("Problem in Queue:");} 
			}
			else
				System.out.println("Receiver client channel is not open");
		  }
		
	  }
	}
	
	public static void main(String [] args)
	{
				
		String nodenumber=args[0];
		Project1.Read_Config();
		int Myport=Integer.parseInt(Project1.Get_IP_from_nodenumber(args[0]).split(":")[1]);
		final Project1 n1=new Project1(Myport);
		Thread ServerStarter1=new Thread(new Runnable(){public void run(){n1.StartServer();}});
		ServerStarter1.start();
		try { Thread.sleep(5000);} catch (InterruptedException ie) {    	}
		n1.Connect_to_Servers();
		BufferedReader br1=null;
		String sCurrentLine=null;
		int receive_count=0;
		String[] inputrecord=null;
		Thread Send_thread=null;
		Thread Receive_thread=null;
		ArrayList<String> tmpList=new ArrayList<String>();
		
		try {br1 = new BufferedReader(new FileReader(System.getProperty("user.home")+"/AOS/input.txt"));
				while ((sCurrentLine = br1.readLine()) != null) 
				{
					inputrecord=sCurrentLine.split("-");
					if(inputrecord[0].equals(nodenumber))
					{
						for(String trec :inputrecord[1].split(","))
						tmpList.add(Project1.Get_IP_from_nodenumber(trec));
						
						final String[] Target_List=tmpList.toArray(new String[0]);
						final String Msg=inputrecord[2];
						
						Send_thread=new Thread(new Runnable(){public void run(){n1.m_send(Target_List,Msg);}});
						Send_thread.start(); 
					}
					if(inputrecord[1].contains(nodenumber))
					{
						Receive_thread=new Thread(new Runnable(){public void run(){n1.m_receive();}});
						Receive_thread.start();
					}
				}
			} catch (Exception e) {e.printStackTrace();}  finally {	try {if (br1 != null)br1.close();} catch (IOException ex) {ex.printStackTrace();}}	
	
	}
}
