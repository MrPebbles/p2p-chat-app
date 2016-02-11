package cps706.p2p;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import cps706.p2p.utils.clientInfo;
import static cps706.p2p.utils.Constants.*;

//TODO: Modify this to suit our needs
//May be moved to separate class
class P2PServerSocket extends P2PProtocol implements Runnable {
	public class MutableLong {
	    private long value;
	    public void setValue(long val) {value = val; }
	    public long getValue() { return this.value; }
	}
	
	
	private int clientid;
	private BufferedReader in = null;
	private PrintStream out = null;
	private Socket sock;
	private int p2pServerPort;
	private String serverType;
	private static ArrayList<clientInfo> clientList;
	private MutableLong lastPongTime = new MutableLong();
	

	public P2PServerSocket(Socket socket, String type, ArrayList<clientInfo> list, int id) throws IOException {
		super(type);
	
		this.sock = socket;
		this.serverType = type;
		this.clientList = list;
		this.clientid = id;
		
        in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
        out = new PrintStream(sock.getOutputStream(), true);
	}
	
	public void run() { 
		boolean isActive = true;
		int type;
		ArrayList<String> receivedData = new ArrayList();
		Thread tPingPong;
		
		synchronized(lastPongTime) {
			lastPongTime.setValue(System.currentTimeMillis());
		}
		
		//Handles timeout
		if(getServerType().equals(DS)) {
			Runnable r = new Runnable() {
				public void run() {
					while(true) {
						long diff;
						synchronized(lastPongTime) {
							diff = System.currentTimeMillis() - lastPongTime.getValue();
						}
	
						if(diff >= 21000) {
							//timeout
							System.out.println("Client(" + clientid + ") has been inactive for 20 seconds\n" +
							"Closing all communications to this client");
							
							//close this thread + socket
							try {
								sock.close();
							} catch (IOException e) {}
							
							break; //exit this thread
						}
					}
					
				}
			};
			
			tPingPong = new Thread(r);
			tPingPong.start();
		}
		

		while(isActive) {
			try {
				receivedData = P2PProtocol.receivePacket(in);
				
				if(isActive == false) 
					break;
				
				type = Integer.parseInt(receivedData.get(MSG_TYPE).toString());
				
				if (getServerType().equals(DS)) {
					if (type == MSG_WHO) {
						System.out.println("P2PClient (ClientID: " + clientid + ") requesting client list...");						
						P2PProtocol.sendData(out, createList(), MSG_WHO, clientid);
					} else if (type == MSG_CONNECT) {
						synchronized(clientList) {
							getClient(clientid).port = Integer.parseInt(receivedData.get(MSG_DATA).toString());
						}
						System.out.println("P2PClient (ClientID: " + clientid + ") connected.");
						P2PProtocol.sendData(out, "", MSG_CONNECT, clientid);
					} else if (type == MSG_DISCONNECT) {
						System.out.println("P2PClient (ClientID: " + clientid + ") terminating...");

						P2PProtocol.sendData(out, "", MSG_DISCONNECT, clientid);
						broadcastMessage("P2PClient (ClientID: " + clientid + ") has disconnected.");
						
						isActive = false;
					} else if (type == MSG_PONG) {
						System.out.println("Ping reply received from P2PClient (ClientID: " + clientid + ").");
						
						synchronized(lastPongTime) {
							lastPongTime.setValue(System.currentTimeMillis());
						}
					} else if (type == MSG_COUNT) {
						String num = receivedData.get(MSG_DATA).split(" ")[0];
						int port = Integer.parseInt(receivedData.get(MSG_DATA).split(" ")[1]);
						
						
						System.out.println("MSG_COUNT: " + num + ", " + port);
						
						synchronized(clientList) {
							for(int i = 0; i < clientList.size(); i++) {
								if(port == clientList.get(i).port) {
									if(num.contains("+1"))
										clientList.get(i).chatRoomCount += 1;
									else
										clientList.get(i).chatRoomCount -= 1;
								}
							}
						}
						
					}
				} else if (getServerType().equals(P2P)) {
					if (type == MSG_CONNECT) {
						
						//use the id recieved from the client (set by DS) to identify this client
						clientid = Integer.parseInt(receivedData.get(CLIENTID));
						System.out.println("P2PClient (ClientID: " + clientid + ") connected.");
						
						//send ack
						P2PProtocol.sendData(out, "", MSG_CONNECT, clientid);
						
					} else if (type == MSG_DISCONNECT) {
						// TODO
					} else if (type == MSG_SEND) {
						String message = "Client(" + clientid + ") says: "
										+ receivedData.get(MSG_DATA).toString();
						broadcastMessage(message);
						System.out.println(message);
					}
				}
			} catch (Exception e) {
				//System.err.println(e);
			}
		}
		
		
		//close socket + remove client
		synchronized(clientList) {
			try { sock.close(); }
			catch (IOException e) {	}
		}
			
		removeClient(clientid);
	}
  
	private static clientInfo getClient(int clientID) {
		synchronized(clientList) {
			for(int i = 0; i < clientList.size(); i++) {
				if(clientID == clientList.get(i).id) {
					return clientList.get(i);
				}
			}
		}
		return null;
	}
	
	
    private static void removeClient(int clientID) {
    	// Client IDs are UNIQUE, don't have to worry about duplicates
    	synchronized(clientList) {
			for(int i = 0; i < clientList.size(); i++) {
				if(clientID == clientList.get(i).id) {
					clientList.remove(i);
					System.out.println("Removed client " + clientID + " from clientList");
					break;
				}
			}
    	}
    }
	
	
	
	void broadcastMessage(String message) throws IOException {
		synchronized(clientList) {
			for(int i = 0; i < clientList.size(); i++) {
				if (!clientList.get(i).equals(null)) {
					Socket client = clientList.get(i).socket;
					PrintStream clientOut = new PrintStream(client.getOutputStream(), true);					
					P2PProtocol.sendData(clientOut, message, MSG_SEND, clientList.get(i).id);
				}
			}
		}
	}
	
	String createList() {
		String str = null;
		synchronized(clientList) {
			for(int i = 0; i < clientList.size(); i++) {
				str += clientList.get(i).id + " ";
				str += clientList.get(i).ip + " ";
				str += clientList.get(i).port + " ";
				str += clientList.get(i).chatRoomCount + " ";
			}
			
		}
		return str;
	}
	
	private String getServerType() {
		return serverType;
	}
}
