package cps706.p2p;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import cps706.p2p.utils.clientInfo;
import static cps706.p2p.utils.Constants.*;

public class DirectoryServer {
	private static ArrayList<clientInfo> clientList = new ArrayList<clientInfo>();
	
    private static int port = PORT_DS; // DS listen port
    private static ServerSocket dsServer;
    private static int ConnectedClients = 0;
    
    public static ArrayList<clientInfo> getClientList() {
    	return clientList;
    }
    
    private static int addClient(Socket sck) {
    	int id;
    	synchronized(clientList) {
        	clientInfo ci = new clientInfo();
        	ci.active = true;
        	ci.socket = sck;
        	ci.ip = sck.getInetAddress().toString().split("/")[1];
        	ci.port = sck.getPort();
        	ci.id = ConnectedClients;
        	ci.chatRoomCount = 0;
        	id = ci.id;
        	clientList.add(ci);
        	ConnectedClients++;
    	}
    	
    	return id;
    }
    
    
    public static void main (String[] args) throws IOException { 
        try {
            dsServer = new ServerSocket(PORT_DS); /* start listening on the port */
            System.out.println("Directory server listening on port " + PORT_DS + ".");
        } catch (IOException e) {
        	System.err.println("Directory server couldn't listen on port " + PORT_DS + ".");
            System.err.println(e);
            System.exit(1);
        }
        
        while(true) {
            try {
            	Socket client = new Socket();
                client = dsServer.accept();
                System.out.println("Client connected: " + client.getInetAddress().toString() 
                		+ ", " + client.getPort());
                               
                // Add client to list
                int clientID = addClient(client);
                
                // Spawn thread to handle the new client
                Thread t = new Thread(new P2PServerSocket(client, DS, clientList, clientID));
                t.start();
                
                try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
                
                // Initialize scheduled ping for this client
                P2PProtocol.scheduledPing(client); // both P2PClient and Directory Server must be able to do this... thinking.

            } catch (IOException e) {
                System.err.println("Accept failed.");
                System.err.println(e);
                System.exit(1);
            }
            
        }
    }
    
}