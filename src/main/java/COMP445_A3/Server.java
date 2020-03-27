package COMP445_A3;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Server {

	public static void main(String[] args) throws IOException {
		
		int listenerPort = 3002;
		DatagramSocket socket = new DatagramSocket(listenerPort);
		byte[] buffer = new byte[1024];
		DatagramPacket request = new DatagramPacket(buffer, buffer.length);
		System.out.println("UDP listener ready..." + listenerPort);
		socket.receive(request);
		System.out.println("Received message on..." + listenerPort);
		System.out.println("Sending reply...");
		
		byte[] packet = new byte[1 + 4 + 4 + 2 + 4];
		byte[] messageFromClient = new byte[4];
		for(int i = 11; i < 11 + 4; i++ ) {
			messageFromClient[i - 11] = buffer[i];
		}
		
		System.out.println("Message from Client: " + new String(messageFromClient));
		
		// Packet Type: 1 byte = 8 bits
		byte packetType = 0x00;
		
		// Sequence number: 4 bytes = 32 bits
		// int unsigned and big-endian
		byte[] sequenceNumberBytes = new byte[4];
		
		// 0x00000002
		sequenceNumberBytes[0] = (byte) 0x00;
		sequenceNumberBytes[1] = (byte) 0x00;
		sequenceNumberBytes[2] = (byte) 0x00;
		sequenceNumberBytes[3] = (byte) 0x02;
		
		// Peer Address: 4 bytes for IPv4
		byte[] peerAddressBytes = new byte[4];
		
		for(int i = 5; i < 9; i++) {
			peerAddressBytes[i - 5] = buffer[i];
		}
		
		// Peer Port Number: 2 bytes big-endian.		
		byte[] peerPortNumberBytes = new byte[2];
		
		for(int i = 9; i < 11; i++) {
			peerPortNumberBytes[i - 9] = buffer[i];
		}
		
		//Payload: 0 to 1013 bytes.
		String payload = "Hi C";
		byte[] payloadBytes = payload.getBytes();
		
		for(int i = 11; i < payloadBytes.length + 11; i++) {
			packet[i] = payloadBytes[i - 11];
		}
		
		packet[0] = packetType;
		
		for(int i = 1; i < 5; i++) {
			packet[i] = sequenceNumberBytes[i - 1];
		}
		
		for(int i = 5; i < 9; i++) {
			packet[i] = peerAddressBytes[i - 5];
		}
		
		for(int i = 9; i < 11; i++) {
			packet[i] = peerPortNumberBytes[i - 9];
		}
		
		for(int i = 11; i < payloadBytes.length; i++) {
			packet[i] = payloadBytes[i - 11];
		}
		
		for(int i = 0; i < packet.length; i++) {
			System.out.print(String.format("0x%02X", packet[i] ) + " ");
//			System.out.println(packet[i].toString);
		}
		
		InetAddress routerAddress = InetAddress.getByName("localhost");
		int routerPort = 3000;
		request = new DatagramPacket(packet, packet.length, routerAddress, routerPort);
		socket.send(request);
		System.out.println("\nPacket sent");
		socket.close();
	}

}
