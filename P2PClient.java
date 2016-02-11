package cps706.p2p;

import static cps706.p2p.utils.Constants.*;
import cps706.p2p.utils.clientInfo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Scanner;

public class P2PClient {
	
	private static boolean DISPLAY_PING = false;
	
	private static BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
	
	// Directory Server
	private static BufferedReader dsIn; 
	private static PrintStream dsOut;
	private static Socket dsSocket;
	private static Thread dsThread;

	// Another P2P client
	private static BufferedReader p2pIn; 
	private static PrintStream p2pOut;
	private static Socket p2pSocket;
	private static Thread p2pThread;
	
	// This P2P client's chat room
	private static Thread chatRoomThread; // 
	private static ServerSocket listenPort; // chat room
	private static int chatRoomPort = PORT_P2P;
	private static int portOffset = 0;

	// Important global variables
	private static int clientID = 0; // Assigned by directory server
	private static ArrayList<clientInfo> clientList = new ArrayList<clientInfo>();
	private static int ConnectedClients = 0; //counter always increments
	private static int extChatRoomPort = 0;
	
    public static ArrayList<clientInfo> getClientList() {
    	return clientList;
    }
    
    private static int addClient(Socket sck) {
    	int id;
    	synchronized(clientList) {
        	clientInfo ci = new clientInfo();
        	ci.socket = sck;
        	ci.ip = sck.getInetAddress().toString().split("/")[1];
        	ci.port = sck.getPort();
        	ci.id = ConnectedClients;
        	id = ci.id;
        	clientList.add(ci);
        	ConnectedClients++;
    	}
    	
    	return id;
    }
    
    private static void removeClient(int clientID) {
    	// Client IDs are UNIQUE, don't have to worry about duplicates
		for(int i = 0; i < clientList.size(); i++) {
			if(clientID == clientList.get(i).id) {
				clientList.remove(i);
				System.out.println("Removed client " + clientID + " from clientList");
				break;
			}
		}
    }
	
	public static void connectToDS() {
		try {
			dsSocket = new Socket("localhost", PORT_DS);
			
			System.out.println("Successfully connected to directory server!");
			
			dsIn = new BufferedReader(new InputStreamReader(dsSocket.getInputStream()));
			dsOut = new PrintStream(dsSocket.getOutputStream(), true);

			P2PProtocol.sendData(dsOut, Integer.toString(chatRoomPort + portOffset), MSG_CONNECT, 0);
			
			initDSResponseThread();
			
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void connectToP2P() throws IOException {
		System.out.print("Enter the PORT# of the remote client: ");
		
		extChatRoomPort = Integer.parseInt(in.readLine());
		
		try {
			p2pSocket = new Socket("localhost", extChatRoomPort);
			
			if (p2pSocket.isConnected())
				System.out.println("Connecting to client...");
			
			p2pIn = new BufferedReader(new InputStreamReader(p2pSocket.getInputStream()));
			p2pOut = new PrintStream(p2pSocket.getOutputStream(), true);
			
			//tell the DS that you are connecting this chatroom
			P2PProtocol.sendData(dsOut, "+1 " + extChatRoomPort, MSG_COUNT, clientID);
			
			//connect to the chatroom
			P2PProtocol.sendData(p2pOut, Integer.toString(chatRoomPort), MSG_CONNECT, clientID);
			
			initP2PResponseThread();
			
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void sendWho() {			
		P2PProtocol.sendData(dsOut, " ", MSG_WHO, clientID);
		System.out.println("Retreiving list of active P2P clients...");
	}
	
	/*
	 * Regular messages should be sent to the client's own or
	 * currently connected chat room.
	 */
	public static void sendMessage(String message) {
		P2PProtocol.sendData(p2pOut, message, MSG_SEND, clientID);
	}
	
	public static void terminateClient() {
		P2PProtocol.sendData(dsOut, "-1 " + extChatRoomPort, MSG_COUNT, clientID);
		P2PProtocol.sendData(dsOut, " ", MSG_DISCONNECT, clientID);
	}
	
	public static void pingDS() {
		P2PProtocol.sendData(dsOut, " ", MSG_PONG, clientID);
	}
	
	public static void receiveData(String origin) {
		ArrayList<String> receivedData = new ArrayList();
		int type;
		
		try {			
			// Parse and display useful information to client here
			if (origin.equalsIgnoreCase(DS)) {
				// Waiting to receive data from directory server
				receivedData = P2PProtocol.receivePacket(dsIn);
				
				// Type of message received from directory server
				type = Integer.parseInt(receivedData.get(MSG_TYPE).toString());
				
				if (type == MSG_CONNECT) {
					clientID = Integer.parseInt(receivedData.get(CLIENTID).toString());
					
					System.out.println("My client ID is " + clientID + "!");
					
				} else if (type == MSG_WHO) {
					String[] list = receivedData.get(MSG_DATA).toString().split(" ");
					
					for(int i = 0; i < list.length - 3; i += 4)
						System.out.println("   ClientID: " + list[i].replace("null", "") 
								+ ", IP: " + list[i+1] + ", Port: " + list[i+2]
								+ ", Count: " + list[i+3]
						);
					
				} else if (type == MSG_SEND) {
					String message = receivedData.get(MSG_DATA).toString();
					System.out.println("DIRECTORY SERVER: " + message);
				} else if (type == MSG_DISCONNECT) {
					System.out.println("Received disconnection acknowledgement from Directory Server, terminating client...");
					if (dsSocket != null) dsSocket.close();
					if (p2pSocket != null) p2pSocket.close();
					if (listenPort != null) listenPort.close();
					
					if (chatRoomThread != null) chatRoomThread.interrupt();
					if (dsThread != null) dsThread.interrupt();
					if (p2pThread != null) p2pThread.interrupt();

					//sleep(1000);
					
					System.out.println("Tata and farewell!");
					
					System.exit(0); // close this client
				} else if (type == MSG_PING) {
					
					if(DISPLAY_PING)
						System.out.println("Ping request received from Directory Server, sending pong...");
					
					pingDS();
				}
			} else if (origin.equalsIgnoreCase(P2P)) {
				// Waiting to receive data from directory server
				receivedData = P2PProtocol.receivePacket(p2pIn);
				
				// Type of message received from directory server
				type = Integer.parseInt(receivedData.get(MSG_TYPE).toString());
				
				if (type == MSG_CONNECT) {
					System.out.println("Connected to P2P client!");
				} else if (type == MSG_SEND) {
					String message = receivedData.get(MSG_DATA).toString();
					System.out.println(message);
				}
			}
		} catch (NumberFormatException e) {
		} catch (IOException e) {
		}
	}
	
	public static void processCommand(String message) throws UnknownHostException, IOException {
		if (message.equalsIgnoreCase("connect ds")) {
			connectToDS();
		} else if (message.equalsIgnoreCase("connect p2p")) {
			connectToP2P();
		} else if (message.equalsIgnoreCase("who")) {
			sendWho();
		} else if (message.equalsIgnoreCase("quit") || message.equalsIgnoreCase("exit") || message.equalsIgnoreCase("close")) {
			terminateClient();
		} else {
			// All messages should be directed to client's chat room
			sendMessage(message);
		}
	}
	
	private static void sleep(int ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	private static void initChatRoom() {
		/*
		 * New thread introduced here within the P2PClient itself in order to host
		 * a chat room to which other chat clients can connect and communicate
		 */
		Runnable r = new Runnable() {
			public void run() {
				System.out.println("Creating chat room...");

				while(true) {
					try {
						listenPort = new ServerSocket(PORT_P2P + portOffset);
						System.out.println("Chat room created successfully on port " + (PORT_P2P + portOffset) + "!");
						break;
					} catch (IOException e) {
						// PORT_P2P + portOffset taken, trying new port
						portOffset++;
					}
				}

				while (true) {
					try {
						Socket p2pSocket = listenPort.accept();
		                System.out.println("Client connected: " + p2pSocket.getInetAddress().toString() 
		                		+ ", " + p2pSocket.getPort());

		                // Add client to list
		                int clientID = addClient(p2pSocket);
						
						try {
							new Thread(new P2PServerSocket(p2pSocket, P2P, clientList, clientID)).start();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					} catch (IOException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
				}
			}
		};

		chatRoomThread = new Thread(r);
		chatRoomThread.start();
	}
	
	private static void initDSResponseThread() {
		Runnable r = new Runnable() {
			public void run() {
				while(true) {
					receiveData(DS);
				}
			}
		};
		
		dsThread = new Thread(r);
		dsThread.start();
	}
	
	private static void initP2PResponseThread() {
		Runnable r = new Runnable() {
			public void run() {
				while(true) {
					receiveData(P2P);
				}
			}
		};
		
		p2pThread = new Thread(r);
		p2pThread.start();
	}
	
	public static void main(String[] args) {
		String buffer = new String();

		initChatRoom();
		
		while(true) {
			sleep(100);
			System.out.print("> ");

			try {
				processCommand(in.readLine());
			} catch (UnknownHostException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} 
	}
}
