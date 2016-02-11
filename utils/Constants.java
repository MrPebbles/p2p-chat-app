package cps706.p2p.utils;

public final class Constants {
	private Constants() {}
	
	// P2P packet structure constants
	public static final int MAX_PACKET_SIZE = 	80;
	public static final int EXT_HEADER_SIZE = 	8;
	public static final int MAX_MESSAGE_SIZE = 	52;
	public static final int TCP_HEADER_SIZE = 	20;		// NOTE: Default TCP packet header contains 20 bytes of data
	
	// P2P packet parsing constants -- MSG_TYPE
	public static final int MSG_CONNECT = 		0;
	public static final int MSG_SEND = 			1;
	public static final int MSG_COUNT = 		2;
	public static final int MSG_WHO = 			3;
	public static final int MSG_DISCONNECT = 	4;
	public static final int MSG_PING = 			5;
	public static final int MSG_PONG =			6;
	
	// Test packet parsing
	public final static int MSG_TYPE =			0;
	public final static int CLIENTID =			1;
	public final static int SEG_NUM =			2;
	public final static int SEG_TOTAL =			3;
	public final static int MSG_DATA =			4;
	
	// P2P protocol ports
	public static final int PORT_DS =			40140;
	public static final int PORT_P2P =			40141;
	
	// P2P protocol orientation
	public static final String DS =				"DS";
	public static final String P2P =			"P2P";
}
