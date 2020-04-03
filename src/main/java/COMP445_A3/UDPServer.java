package COMP445_A3;


import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.AcceptPendingException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Stream;

import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;

public class UDPServer {

    private static final Logger logger = LoggerFactory.getLogger(UDPServer.class);

    private void listenAndServe(int port) throws IOException {

        try (DatagramChannel channel = DatagramChannel.open()) {
            channel.bind(new InetSocketAddress(port));
            logger.info("EchoServer is listening at {}", channel.getLocalAddress());
            ByteBuffer buf = ByteBuffer
                    .allocate(Packet.MAX_LEN)
                    .order(ByteOrder.BIG_ENDIAN);

            long SYN;
            String[] payloads = new String[0];
            boolean[] ACKs = new boolean[0];
            boolean ReceivedAllData = false;

            while (true) {
                buf.clear();
                SocketAddress router = channel.receive(buf);

                // Parse a packet from the received raw data.
                buf.flip();
                Packet packet = Packet.fromBuffer(buf);
                buf.flip();

                //first handshake
                if (packet.getType() == 2) {
                    logger.info("received first handshake");
                    SYN = packet.getSequenceNumber();
                    payloads = new String[(int) SYN];
                    ACKs = new boolean[(int)SYN];
                    ReceivedAllData = false;
                    logger.info("SYN: {}", SYN);
                    String payload = "";
                    logger.info("Packet: {}", packet);
                    logger.info("Payload: {}", payload);
                    logger.info("Router: {}", router);
                    // Send the response to the router not the client.
                    // The peer address of the packet is the address of the client already.
                    // We can use toBuilder to copy properties of the current packet.
                    // This demonstrate how to create a new packet from an existing packet.
                    // send second handshake
                    Packet resp = packet.toBuilder()
                            .setType(3)
                            .setSequenceNumber(packet.getSequenceNumber())
                            .setPayload(payload.getBytes())
                            .create();
                    channel.send(resp.toBuffer(), router);
                    logger.info("sending second handshake");
                }

                // third handshake
                if (packet.getType() == 1){
                    logger.info("received third handshake");
                    long seq = packet.getSequenceNumber();
                    String payload = "";
                    logger.info("Packet: {}", packet);
                    logger.info("Payload: {}", payload);
                    logger.info("Router: {}", router);
                    logger.info("Allow to start data transmission");
                }

                // received data
                if (packet.getType() == 0){
                    logger.info("received Data packet");
                    long seq = packet.getSequenceNumber();
                    if (ACKs[(int)seq]){
                        logger.info("Server have already received this packet {}.", seq);
                        Packet ACK = packet.toBuilder()
                                .setType(1)
                                .setSequenceNumber(packet.getSequenceNumber())
                                .setPayload("".getBytes())
                                .create();
                        channel.send(ACK.toBuffer(), router);
                        logger.info("sending Data ACK");
                        continue;
                    } else {
                        String payload = new String(packet.getPayload());
                        payloads[(int)seq] = payload;
                        ACKs[(int)seq] = true;
                        logger.info("Packet: {}", packet);
                        logger.info("Payload: {}", payload);
                        logger.info("Router: {}", router);
                        // Send the response to the router not the client.
                        // The peer address of the packet is the address of the client already.
                        // We can use toBuilder to copy properties of the current packet.
                        // This demonstrate how to create a new packet from an existing packet.
                        // send second handshake
                        Packet ACK = packet.toBuilder()
                                .setType(1)
                                .setSequenceNumber(packet.getSequenceNumber())
                                .setPayload("".getBytes())
                                .create();
                        channel.send(ACK.toBuffer(), router);
                        logger.info("sending Data ACK");
                    }
                }

                boolean getAllData = true;
                for (int o = 0; o < ACKs.length; o++){
                    if (!ACKs[o]){
                        getAllData = false;
                        break;
                    }
                }

                if (getAllData){
                    StringBuilder request = new StringBuilder();
                    for (int p = 0; p < payloads.length; p++){
                        request.append(payloads[p]);
                    }
                    ReceivedAllData = true;
                    logger.info("received all data: ");
                    logger.info("{}", request.toString());
                    logger.info("request length: {}", request.length());

                }

                // ToDo: reply to client
                // use variable "request"
                if (ReceivedAllData){

                    String input = new String(packet.getPayload());
                    String[] inputSplited = input.split("\\s+");

                    if (inputSplited[0].equalsIgnoreCase("GET")) {
                        getResponse(inputSplited);
                    }else if (inputSplited[0].equalsIgnoreCase("POST")) {
                        postResponse(inputSplited);
                    }else {
                        System.out.println("Error, could not read the UDPClient request");
                    }

                }
            }
        }
    }


    private static String getResponse(String[] inputSplited) {

        String body = "";
        String response = "";
        String dir = "/Users/brent/Documents/GitHub/COMP_445_A3/src/main/java/COMP445_A3/data";

        final Date currentTime = new Date();
        final SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d, yyyy hh:mm:ss a z");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

        if (inputSplited[0].equalsIgnoreCase("get")){
//					 create GET body
//					 GET /

                String tdir = dir + inputSplited[1];
                // response file content
                if (inputSplited[1].contains(".")){
                    StringBuilder contentBuilder = new StringBuilder();
                    // NEED change path before running
                    try (Stream<String> stream = Files.lines(Paths.get(tdir), UTF_8)){
                        stream.forEach(s -> contentBuilder.append(s).append("\n"));
                        body = contentBuilder.toString();
                        // transfer file
                        response = "HTTP/1.0 200 ok\r\n" +
                                    "Date: " + sdf.format(currentTime) + "\r\n" +
                                    "Content-Disposition: inline \r\n" +
                                    "Content-Disposition: attachment; filename=\"" + inputSplited[1] + "\"\r\n" +
                                    "Content-Type: application/json\r\n" +
                                    "Content-Length: " + body.length() + "\r\n" +
                                    "Connection: close\r\n" +
                                    "Access-Control-Allow-Origin: *\r\n" +
                                    "Access-Control-Allow-Credentials: true\r\n\r\n" +
                                    body;

                    }
                    catch (NoSuchFileException e) {
                        System.out.println(3);
                        body = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">\n" +
                                "<title>404 Not Found</title>\n" +
                                "<h1>Not Found</h1>\n" +
                                "<p>The requested URL was not found on the server.  If you entered the URL manually please check your spelling and try again.</p>";
                        response = "HTTP/1.0 404 Not Found\r\n" +
                                "Server: \r\n" +
                                "Date: " + sdf.format(currentTime) + "\r\n" +
                                "Content-Type: text/html\r\n" +
                                "Content-Length: " + body.length() + "\r\n" +
                                "Connection: close\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "Access-Control-Allow-Credentials: true\r\n\r\n" +
                                body;
                    }
                    catch (IOException e) {
                        body = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 3.2 Final//EN\">\n" +
                                "<title>400 Bad Request</title>\n" +
                                "<h1>Bad Request</h1>\n" +
                                "<p>The server cannot read the request.</p>";
                        response = "HTTP/1.0 400 Bad Request\r\n" +
                                "Server: \r\n" +
                                "Date: " + sdf.format(currentTime) + "\r\n" +
                                "Content-Type: text/html\r\n" +
                                "Content-Length: " + body.length() + "\r\n" +
                                "Connection: close\r\n" +
                                "Access-Control-Allow-Origin: *\r\n" +
                                "Access-Control-Allow-Credentials: true\r\n\r\n" +
                                body;
                    }
                }


                // response list of directory
                else {
                    try {
                        File f = new File(tdir);
                        String[] pathnames = f.list();
                        System.out.println("PathList: ");
                        for (String pathname : pathnames) {
                            System.out.println(pathname);
                            body = body + pathname + "\r\n";
                        }

                        response = 	"HTTP/1.0 200 ok\r\n"
                                + "Data: " + sdf.format(currentTime) + "\r\n"
                                + "Content-Length: " + body.length() + "\r\n"
                                + "Content-Type: application/json\r\n\r\n"
                                + body;

                    } catch (NullPointerException e) {
                        System.out.println(4);
                        body = "<!DOCTYPE HTML>\n" +
                                "<title>404 Not Found</title>\n" +
                                "<h1>Not Found</h1>\n" +
                                "<p>The requested URL was not found on the server.  If you entered the URL manually please check your spelling and try again.</p>";

                        response = 	"HTTP/1.0 404 Not Found\r\n"
                                + "Data: " + sdf.format(currentTime) + "\r\n"
                                + "Content-Length: " + body.length() + "\r\n"
                                + "Content-Type: text/html\r\n\r\n"
                                + body;
                    }
                }

        }
        System.out.println("Response sent to client" + response);

        return response;
    }

    private static String postResponse(String[] inputSplited) {



        int indexOfContentLength = 0;
        int indexOfUserAgent = 0;

        String body = "";
        String response = "";
        String postData = "";
        String dir = "/Users/brent/Documents/GitHub/COMP_445_A3/src/main/java/COMP445_A3/data";

        final Date currentTime = new Date();
        final SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d, yyyy hh:mm:ss a z");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

        for (int i = 0; i < inputSplited.length; i++){
            if (inputSplited[i].equalsIgnoreCase("Content-Length:")){
                indexOfContentLength = i+1;
            } else if (inputSplited[i].equalsIgnoreCase("User-Agent:")){
                indexOfUserAgent = i+1;
            }
        }

//        for (int i = 0; i < Integer.parseInt(inputSplited[indexOfContentLength]); i++){
//
//            data = inputStream.read();
//            postData = postData + (char)data;
//        }
//        request.append("\r\n");
//        request.append(postData);


        for (int i = 0; i < inputSplited.length; i++) {
            if (inputSplited[i].equalsIgnoreCase("data:")) {
                postData = inputSplited[i+1];
            }
        }



        if (inputSplited[0].equalsIgnoreCase("post")){
            //create POST body
            //POST /

            String dataPart = "\t\"data\": \"{\"";
            body = "{\r\n" +
                    "\t\"args\": {},\r\n";
            if (inputSplited[1].equalsIgnoreCase("/")){
                String[] Sarg = postData.split("&");
                String[][] key = new String[Sarg.length][2];
                for (int i = 0; i < Sarg.length; i++){
                    String[] tem = Sarg[i].split("=");
                    key[i][0] = tem[0];
                    key[i][1] = tem[1];
                }

                for (int i = 0; i < key.length; i++){

                    if (i != key.length-1){
                        dataPart = dataPart + "\"" + key[i][0] + "\": " + key[i][1] + ", ";
                    } else {
                        dataPart = dataPart + "\"" + key[i][0] + "\": " + key[i][1] + "}\",\r\n";
                    }
                }
                String filesPart = "\t\"files\": {},\r\n";
                String formPart = "\t\"form\": {},\r\n";
                String headersPart = "\t\"headers\": {\r\n" +
                        "\t\t\"Content-Length\": \"" + Integer.parseInt(inputSplited[indexOfContentLength]) + ", \r\n" +
                        "\t\t\"Content-Type\": \"application/json\",\r\n" +
                        "\t\t\"Host\": \"localhost\",\r\n" +
                        "\t\t\"User-Agent\": " + inputSplited[indexOfUserAgent] + "\"\r\n" +
                        "\t},\r\n";
                String jsonPart = "\t\"json\": {\r\n" + "\t},\r\n";
                String urlPart = "\t\t\"url\": \"http://localhost/post\"\r\n" +
                        "}";
                body = body + dataPart + filesPart + formPart + headersPart + jsonPart + urlPart;


                response = 	"HTTP/1.0 200 ok\r\n"
                        + "Data: " + sdf.format(currentTime) + "\r\n"
                        + "Content-Length: " + body.length() + "\r\n"
                        + "Content-Type: application/json\r\n\r\n"
                        + body;
            }

            else {
                // post to overwrite file
                if (inputSplited[1].contains(".")){
                    try {
                        String filedir = dir+inputSplited[1];
                        if (filedir.equalsIgnoreCase("/Users/brent/Documents/GitHub/COMP_445_A3/src/main/java/COMP445_A3/data/readonly.txt") ||
                                filedir.equalsIgnoreCase("/Users/brent/Documents/GitHub/COMP_445_A3/src/main/java/COMP445_A3/UDPServer.java") ){
                            throw new Exception("1");
                        }
                        PrintWriter writer = new PrintWriter(dir + inputSplited[1], "UTF-8");
                        writer.print(postData);
                        writer.close();
                        body = "<!DOCTYPE HTML>\n" +
                                "<title>Success</title>\n" +
                                "<h1>Success</h1>\n";;
                        response = 	"HTTP/1.0 200 ok\r\n"
                                + "Data: " + sdf.format(currentTime) + "\r\n"
                                + "Content-Length: " + body.length() + "\r\n"
                                + "Content-Type: text/html\r\n\r\n"
                                + body;
                    } catch (Exception e){
                        if (e.getMessage().equalsIgnoreCase("1")){
                            body = "<!DOCTYPE HTML>\n" +
                                    "<title>400 Bad Request</title>\n" +
                                    "<h1>Bad Request</h1>\n" +
                                    "<p>The file is read only.</p>";
                        } else {
                            body = "<!DOCTYPE HTML>\n" +
                                    "<title>400 Bad Request</title>\n" +
                                    "<h1>Bad Request</h1>\n" +
                                    "<p>The server cannot read the request.</p>";
                        }

                        response = 	"HTTP/1.0 400 bad request\r\n"
                                + "Data: " + sdf.format(currentTime) + "\r\n"
                                + "Content-Length: " + body.length() + "\r\n"
                                + "Content-Type: application/json\r\n\r\n"
                                + body;
                    }

                }
                // undeclared file name
                else {
                    body = "<!DOCTYPE HTML>\n" +
                            "<title>400 Bad Request</title>\n" +
                            "<h1>Bad Request</h1>\n" +
                            "<p>The server cannot read the request.</p>";
                    response = 	"HTTP/1.0 400 bad request\r\n"
                            + "Data: " + sdf.format(currentTime) + "\r\n"
                            + "Content-Length: " + body.length() + "\r\n"
                            + "Content-Type: application/json\r\n\r\n"
                            + body;
                }

            }

        }
        System.out.println("Response sent to client\n" + response);

        return response;
    }


    public static void main(String[] args) throws IOException {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(asList("port", "p"), "Listening port")
                .withOptionalArg()
                .defaultsTo("8007");

        OptionSet opts = parser.parse(args);
        int port = Integer.parseInt((String) opts.valueOf("port"));
        UDPServer server = new UDPServer();
        server.listenAndServe(port);
    }
}
