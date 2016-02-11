package cps706.p2p;

import java.net.Socket;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;

import static cps706.p2p.utils.Constants.*;

public class P2PProtocol extends Socket {	
	private static final boolean DEBUG_MODE = false;
	
	private static String clientType = null;

	/*
	 * Specify who will be utilizing this custom protocol (DS or P2P client)
	 * as this will allow us to parse certain message types differently. 
	 * For example, MSG_DISCONNECT for P2P client will send a quit message
	 * to P2P server and server will broadcast the message. However, when
	 * the DS server receives a MSG_DISCONNECT, it may just remove the 
	 * P2P client's ID and update the list of active P2P client-servers.
	 */
	
	/* TODO: Initialize TCP inbound/outbound connections
	 * Our protocol will include functionality for creating a server socket
	 * or a client socket. Both DS and P2P clients will utilize server sockets
	 * whereas only P2P clients will utilize a client socket as well.
	 */
	public P2PProtocol(String type) {
		setClientType(type);
	}
	
	private void setClientType(String type) {
		if (type.equalsIgnoreCase("DS")) {
			clientType = "DS";
			// P2PServerSocket
		} else if (type.equalsIgnoreCase("P2P")) {
			clientType = "P2P";
			// P2PClientSocket -- P2P client must connect to DS first, then initiate connection to another P2P client
			// P2PServerSocket
		}
	}
	
	public static int hexToInt(String hex) {
		return Integer.parseInt(hex, 16);
	}
	
	private static String buildHeader(int msgType, int clientID, int seqNum, int seqTotal) {
		String header = new String();
		
		header += String.format("%02X", msgType);	//1 byte
		header += String.format("%02X", clientID);	//1 byte
		header += String.format("%02X", seqNum);	//1 byte
		header += String.format("%02X", seqTotal);	//1 byte
		
		return header;
	}
	
	public static ArrayList<String> preparePacket(int msgType, int clientID, String message) {
		ArrayList<String> packets = new ArrayList();
		String buffer = new String();
		String msgSeg = new String();
		
		if (message.length() + TCP_HEADER_SIZE + EXT_HEADER_SIZE <= MAX_PACKET_SIZE) {
			buffer += buildHeader(msgType, clientID, 1, 1);
			buffer += message;
			
			packets.add(buffer);
		} else {			
			int seqTotal = (int) Math.ceil((double) message.length() / MAX_MESSAGE_SIZE);
			int newStartPos = 0;
			
			for (int i = 1; i <= seqTotal; i++) { // Begin segment numbering at 1 rather than 0
				buffer = "";
				
				if (message.length() > MAX_MESSAGE_SIZE) {
					msgSeg = message.substring(0, MAX_MESSAGE_SIZE);
					newStartPos = MAX_MESSAGE_SIZE;
				} else {
					msgSeg = message.substring(0,message.length());
					newStartPos = message.length();
				}
				message = message.substring(newStartPos);
				
				buffer += buildHeader(msgType, clientID, i, seqTotal);
				buffer += msgSeg;
				
				packets.add(buffer);
			}
		}
		
		if (DEBUG_MODE) {
			System.out.println("DEBUG: P2PProtocol.java -> preparePacket()");
			for (int i = 0; i < packets.size(); i++) {
				System.out.println("Packet length: " + packets.get(i).length() + "\nPacket data: " + packets.get(i).toString());
				System.out.println();
			}
		}
		
		return packets;
	}
	
	public static ArrayList<String> parsePacket(String packet) {
		ArrayList<String> packetParams = new ArrayList();
		
		packetParams.add(Integer.toString(hexToInt(packet.substring(0, 2))));
		packetParams.add(Integer.toString(hexToInt(packet.substring(2, 4))));
		packetParams.add(Integer.toString(hexToInt(packet.substring(4, 6))));
		packetParams.add(Integer.toString(hexToInt(packet.substring(6, 8))));
		packetParams.add(packet.substring(EXT_HEADER_SIZE));
		
		if (DEBUG_MODE) {
			System.out.println("DEBUG: P2PProtocol.java -> packetParser()");
			System.out.println(
					"Packet Parameters:"
					+ "\nmsgType: " + packetParams.get(0).toString()
					+ "\nclientID: " + packetParams.get(1).toString()
					+ "\nseqNum: " + packetParams.get(2).toString()
					+ "\nseqTotal: " + packetParams.get(3).toString()
					+ "\nMessage: " + packetParams.get(4).toString());
			System.out.println();
		}
		
		return packetParams;
	}
	
	// Private -- not optional on our protocol
	public static void scheduledPing(final Socket socket) {
		Timer timer = new Timer();
		long pingInterval = 10000;	// 10 seconds (10*1000ms)
		
		timer.schedule(new TimerTask() {		
			
			@Override
			public void run() {
				try {
					if(socket == null || socket.isClosed())
						this.cancel();
					else
						sendPing(socket);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}, 0, pingInterval);
	}
	
	// Send ping to this clientID
	private static void sendPing(Socket socket) throws IOException {
		ArrayList<String> pingPacket = new ArrayList();
		PrintStream out = new PrintStream(socket.getOutputStream());
		
		// No data in ping packet
		//pingPacket = preparePacket(MSG_PING, 0, "");
		
		//sendPacket(out, pingPacket);
		
		sendData(out, "", MSG_PING, 0);
		
		if (DEBUG_MODE) {
			Date currentDate = new Date();	// Fetch current date
			Timestamp currentTime = new Timestamp(currentDate.getTime());	// Create time stamp
			
			System.out.println("DEBUG: P2PProtocol.java -> sendPing()");
			System.out.println("[" + String.format("%1$TT", currentTime) + "] Ping sent to clientID(" + (int) (Math.random() * 0xFF) + ").");
			System.out.println();
		}
	}
	
	// Send multiple packets
	public static void sendPacket(PrintStream out, ArrayList<String> packets) {		
		for (int i = 0; i < packets.size(); i++) {
			out.println(packets.get(i).toString());
			//out.flush();
		}
	}
	
	// Send single packet
	public static void sendPacket(PrintStream out, String packet) {
		// Could also use println to write to outbound socket stream, but need to consider 2 bytes for message termination
		out.println(packet);
	}
	 
	public static ArrayList<String> receivePacket(BufferedReader in) throws NumberFormatException, IOException {
		int segmentsReceived = 0;
		String buffer = new String();

		//System.out.println("Thread created!");
		String inboundPacket = new String();
		ArrayList<String> packetInfo = new ArrayList();
		ArrayList<String> receivedData = new ArrayList();
		
		while ((inboundPacket = in.readLine()) != null) {
			segmentsReceived++;
			// Parse packet
			packetInfo = parsePacket(inboundPacket);

			receivedData = packetInfo;
/*
//			if (Integer.parseInt(packetInfo.get(MSG_TYPE).toString()) == MSG_SEND) {
//				buffer += packetInfo.get(MSG_DATA).toString();
//			}
*/
			
			if(!packetInfo.get(MSG_DATA).toString().isEmpty()) 
				buffer += packetInfo.get(MSG_DATA).toString();
			
			if (Integer.parseInt(packetInfo.get(SEG_TOTAL).toString()) == segmentsReceived) {
				receivedData.set(MSG_DATA, buffer);
				break;
			}
		}

		return receivedData;
	}
	
	public static void sendData(PrintStream out, String msg, int type, int id) {
		ArrayList<String> packets = preparePacket(type, id, msg);
		P2PProtocol.sendPacket(out, packets);
	}
	
}


