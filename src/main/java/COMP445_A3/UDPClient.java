package COMP445_A3;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static java.nio.channels.SelectionKey.OP_READ;

public class UDPClient {

    private static final Logger logger = LoggerFactory.getLogger(UDPClient.class);

    private static boolean ClientSendTo (SocketAddress routerAddr, InetSocketAddress serverAddr, String request) throws IOException {
        try(DatagramChannel channel = DatagramChannel.open()){
            int dataLength = request.getBytes().length;
            long NumOfPacket = dataLength/1013;
            boolean transferDone = false;

            // create packets list
            Packet[] packets = new Packet[(int) NumOfPacket+1];
            for (int i = 0; i <= NumOfPacket; i++){
                if (i == NumOfPacket){
                    logger.info("payload length: {}", request.substring(i*1013).getBytes().length);
                    packets[i] = new Packet.Builder()
                        .setType(0)
                        .setSequenceNumber(i)
                        .setPortNumber(serverAddr.getPort())
                        .setPeerAddress(serverAddr.getAddress())
                        .setPayload(request.substring(i*1013).getBytes())
                        .create();
//                    logger.info("{}", request.substring(i*1014-1).getBytes().length);
                } else {
                    packets[i] = new Packet.Builder()
                        .setType(0)
                        .setSequenceNumber(i)
                        .setPortNumber(serverAddr.getPort())
                        .setPeerAddress(serverAddr.getAddress())
                        .setPayload(request.substring(i*1013, 1013+i*1013).getBytes())
                        .create();
//                    logger.info("{}", request.substring(i*1014, 1013+i*1014).getBytes().length);
                }
            }

            // Three-way handshake

            // First handshake
            while (true){
                // create packet for handshake 1
                Packet HS1 = new Packet.Builder()
                        .setType(2)     // type: SYN
                        .setSequenceNumber(NumOfPacket+1)      // SYN
                        .setPortNumber(serverAddr.getPort())
                        .setPeerAddress(serverAddr.getAddress())
                        .setPayload("".getBytes())
                        .create();
                // send first handshake packet to server
                channel.send(HS1.toBuffer(), routerAddr);

                logger.info("Sending first handshake to router at {}", routerAddr);

                // Try to receive second handshake within timeout.
                channel.configureBlocking(false);
                Selector selector = Selector.open();
                channel.register(selector, OP_READ);
                logger.info("Waiting for the Second handshake");
                selector.select(500);

                Set<SelectionKey> keys = selector.selectedKeys();
                // If not get response, will resent first handshake
                if(keys.isEmpty()){
                    logger.error("No response after timeout");
                }
                // if get response
                else {
                    ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                    SocketAddress router = channel.receive(buf);
                    buf.flip();
                    Packet resp = Packet.fromBuffer(buf);
                    logger.info("received second handshake: ");
                    logger.info("Packet: {}", resp);
                    logger.info("Router: {}", router);
                    logger.info("Sequence Number: {}", resp.getSequenceNumber());
                    String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
                    logger.info("Payload: {}",  payload);

                    Packet HS3 = new Packet.Builder()
                            .setType(1)
                            .setSequenceNumber(0L)
                            .setPortNumber(serverAddr.getPort())
                            .setPeerAddress(serverAddr.getAddress())
                            .setPayload("".getBytes())
                            .create();
                    channel.send(HS3.toBuffer(), routerAddr);
                    logger.info("Sending third handshake to router at {}", routerAddr);

                    keys.clear();

                    break;
                }
            }

            // Starting transfer data
            logger.info("Start to sending data to router {}", routerAddr);
            int windowSize = 4;
            boolean[] ACKs = new boolean[packets.length];

            while (true) {
                // if packets number is less or equal than window size
                // maximum of payload is 4052 bytes
                if (packets.length <= windowSize){
                    for (int i = 0; i < packets.length; i++){
                        if (!ACKs[i]){
                            channel.send(packets[i].toBuffer(), routerAddr);
                            logger.info("Sending packet {}", packets[i].getSequenceNumber());
                            logger.info("Payload: {}", new String(packets[i].getPayload()));
                            logger.info("Payload length: {}", new String(packets[i].getPayload()).length());
                        }
                    }
                    // Try to receive Data ACK
                    for (int i = 0; i < packets.length; i++){
                        channel.configureBlocking(false);
                        Selector selector = Selector.open();
                        channel.register(selector, OP_READ);
                        logger.info("Waiting for the Data ACK");
                        selector.select(500);

                        Set<SelectionKey> keys = selector.selectedKeys();
                        // If not receive Data ACK
                        if (keys.isEmpty()) {
                            logger.error("No response after timeout");
                        } else {
                            // if receive Data ACK
                            ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                            SocketAddress router = channel.receive(buf);
                            buf.flip();
                            Packet resp = Packet.fromBuffer(buf);
                            logger.info("received Data ACK: ");
                            logger.info("Packet: {}", resp);
                            logger.info("Router: {}", router);
                            logger.info("Sequence Number: {}", resp.getSequenceNumber());
                            ACKs[(int) resp.getSequenceNumber()] = true;
                            String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
                            logger.info("Payload: {}", payload);
                            // display ACKs
//                            for (int o = 0; o < ACKs.length; o++){
//                                logger.info("ACKs[{}]: {}", o, ACKs[o]);
//                            }
                            keys.clear();
                            boolean getAllACKs = true;
                            for (int p = 0; p < ACKs.length; p++){
                                if (!ACKs[p]){
                                    getAllACKs = false;
                                }
                            }
                            if (getAllACKs){
                                break;
                            }
                        }
                    }
                    // check all the packets get ACK.
                    boolean getAllACKs = true;
                    for (int p = 0; p < ACKs.length; p++){
                        if (!ACKs[p]){
                            getAllACKs = false;
                        }
                    }
                    if (getAllACKs){
                        logger.info("Data transfer success");
                        transferDone = true;
                        return transferDone;
                    }
                }
                // if packets number is more than window size
                else {
                    // Sending first window size data = 4
                    int windowStart = 0;
                    int windowEnd = 3;
                    for (int i = 0; i < windowSize; i++){
                        if (!ACKs[i]){
                            channel.send(packets[i].toBuffer(), routerAddr);
                            logger.info("Sending packet {}", packets[i].getSequenceNumber());
                            logger.info("Payload: {}", new String(packets[i].getPayload()));
                            logger.info("Payload length: {}", new String(packets[i].getPayload()).length());
                        }
                    }
                    // Try to receive Data ACK
                    while (true){
                        channel.configureBlocking(false);
                        Selector selector = Selector.open();
                        channel.register(selector, OP_READ);
                        logger.info("Waiting for the Data ACK");
                        selector.select(500);

                        Set<SelectionKey> keys = selector.selectedKeys();
                        // If not receive Data ACK
                        if (keys.isEmpty()) {
                            logger.error("No response after timeout");
                            for (int o = windowStart; o <= windowEnd; o++){
                                if (!ACKs[o]){
                                    logger.info("sending packet #{}", packets[o].getSequenceNumber());
                                    channel.send(packets[o].toBuffer(), routerAddr);
                                }
                            }
                        } else {
                            // if receive Data ACK
                            ByteBuffer buf = ByteBuffer.allocate(Packet.MAX_LEN);
                            SocketAddress router = channel.receive(buf);
                            buf.flip();
                            Packet resp = Packet.fromBuffer(buf);
                            logger.info("received Data ACK: {}", resp.getSequenceNumber());
//                            logger.info("Packet: {}", resp);
//                            logger.info("Router: {}", router);
//                            logger.info("Sequence Number: {}", resp.getSequenceNumber());
                            ACKs[(int) resp.getSequenceNumber()] = true;
                            if (resp.getSequenceNumber() == windowStart){
                                for (int i = windowStart; i < ACKs.length; i++){
                                    if (!ACKs[i]){
                                        windowStart = i;
                                        if (windowStart+3 < packets.length){
                                            windowEnd = windowStart+3;
                                        } else {
                                            windowEnd = packets.length-1;
                                        }
                                        break;
                                    }
                                }
                            }
//                            String payload = new String(resp.getPayload(), StandardCharsets.UTF_8);
//                            logger.info("Payload: {}", payload);
                            // display ACKs
//                            for (int o = 0; o < ACKs.length; o++){
//                                logger.info("ACKs[{}]: {}", o, ACKs[o]);
//                            }
                            keys.clear();
                            boolean getAllACKs = true;
                            for (int p = 0; p < ACKs.length; p++){
                                if (!ACKs[p]){
                                    getAllACKs = false;
                                }
                            }
                            if (getAllACKs){

                                break;
                            }

                            //send next window packets
                            boolean windowSendDone = true;
                            for (int i = windowStart; i <= windowEnd; i++){
                                if (!ACKs[i]){
                                    windowSendDone = false;
//                                    channel.send(packets[i].toBuffer(), routerAddr);
//                                    logger.info("Sending packet {}", i);
                                }
                            }
                            if (windowSendDone) {
                                for (int i = windowStart; i <= windowEnd; i++){
                                    if (!ACKs[i]){
                                        channel.send(packets[i].toBuffer(), routerAddr);
                                        logger.info("Sending packet {}", i);
                                    }
                                }
                            }
                        }
                    }
                    logger.info("transmation done.");
                    transferDone = true;
                    return transferDone;
                }
            }


        }
    }

    private static String toRequest(String input) {

        String request = "";
        String[] inputSplited = input.split("\\s+");
        String path = "";

        if (inputSplited[0].equalsIgnoreCase("GET")) {

            for (int i = 0; i < inputSplited.length; i++) {
                if (inputSplited[i].contains("http://")) {
                    path = inputSplited[1].substring(21);
                }
            }

            request = "GET "+ path +" /localhost:8007 HTTP/1.1\n" +
                    "User-Agent: Mozilla/4.0 (compatible; MSIE5.01; Windows NT)\n" +
                    "Host: www.tutorialspoint.com\n" +
                    "Accept-Language: en-us\n" +
                    "Accept-Encoding: gzip, deflate\n" +
                    "Connection: Keep-Alive";

        }

        else if (inputSplited[0].equalsIgnoreCase("POST")) {

            String data = "";

            for (int i = 0; i < inputSplited.length; i++) {
                if (inputSplited[i].contains("http://")) {
                    path = inputSplited[i].substring(21);
                }
            }

            for (int j = 0; j < input.length(); j++){
                if (input.charAt(j) == '\"'){
                    j++;
                    for (int k = j; k < input.length(); k++){
                        if (input.charAt(k) == '\"'){
                            break;
                        } else{
                            data += input.charAt(k);
                        }
                    } break;
                }
            }

            request = "POST "+ path +" /localhost:8007 HTTP/1.1\n" +
                    "User-Agent: Mozilla/4.0 (compatible; MSIE5.01; Windows NT)\n" +
                    "Host: www.tutorialspoint.com\n" +
                    "Accept-Language: en-us\n" +
                    "Accept-Encoding: gzip, deflate\n" +
                    "Connection: Keep-Alive\n" +
                    "\n\n" +
                    "Data: " + data + "\n";

        }

        else {

            request = "N/A";

        }


        return request;
    }

    public static void main(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        parser.accepts("router-host", "Router hostname")
                .withOptionalArg()
                .defaultsTo("localhost");

        parser.accepts("router-port", "Router port number")
                .withOptionalArg()
                .defaultsTo("3000");

        parser.accepts("server-host", "EchoServer hostname")
                .withOptionalArg()
                .defaultsTo("localhost");

        parser.accepts("server-port", "EchoServer listening port")
                .withOptionalArg()
                .defaultsTo("8007");

        OptionSet opts = parser.parse(args);

        // Router address
        String routerHost = (String) opts.valueOf("router-host");
        int routerPort = Integer.parseInt((String) opts.valueOf("router-port"));

        // Server address
        String serverHost = (String) opts.valueOf("server-host");
        int serverPort = Integer.parseInt((String) opts.valueOf("server-port"));
        SocketAddress routerAddress = new InetSocketAddress(routerHost, routerPort);
        InetSocketAddress serverAddress = new InetSocketAddress(serverHost, serverPort);

//        StringBuilder request = new StringBuilder();
//        int numC = 20000;
//        for (int i = 0; i < numC; i++){
//            request.append("1");
//        }

        // user input

        String inputGet1 = "GET http://localhost:8080/";

        String inputGet2 = "GET http://localhost:8080/1/upload.txt";

        String inputPost1 = "POST --data \"update data\" http://localhost:8080/1";

        String inputPost2 = "POST --data \"param1=value1&param2=value2\" http://localhost:8080/jason.txt";

        String request = "";

//        String request = "GET /localhost:8007 HTTP/1.1\n" +
//                "User-Agent: Mozilla/4.0 (compatible; MSIE5.01; Windows NT)\n" +
//                "Host: www.tutorialspoint.com\n" +
//                "Accept-Language: en-us\n" +
//                "Accept-Encoding: gzip, deflate\n" +
//                "Connection: Keep-Alive";

        request = toRequest(inputGet2);

        logger.info("request: {}", request);
        logger.info(request.toString());

        boolean sendDone = ClientSendTo(routerAddress, serverAddress, request); //.toString()

        //Todo: start receive


    }
}

