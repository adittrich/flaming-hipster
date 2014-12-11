package gossip.stat.server;

import gossip.stat.tools.Util;

import java.net.InetAddress;
//import java.net.NetworkInterface;
import java.net.SocketException;
//import java.util.Enumeration;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

public class Main {

	public static void main(String[] args) {
		
		Options options = new Options();
		
		options.addOption("n", true, "Use this Network Interface's InetAdress as endpoint. Default eth0");
		options.addOption("p", true, "Use this port as endpoint. Default 8000");
		options.addOption("h", false, "Display this message");
		options.addOption("a", true, "Analytics mode with filename to perform analysis on. No default");
		
		
		CommandLineParser parser = new PosixParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options,args);
		} catch (ParseException e) {
			e.printStackTrace();
			System.exit(1);;
		}
		
		// automatically generate the help statement
		HelpFormatter formatter = new HelpFormatter();
		if (cmd.hasOption("h")) {
			formatter.printHelp("cyclonClient", options);
			System.exit(0);
		}	
		
      	//get InetAddress IP of specified network interface
		InetAddress serverAddress = null;
		try { 
			serverAddress = Util.getLocalAddressbyName(cmd.hasOption("n") ? cmd.getOptionValue("n") : "eth0");
		} catch (SocketException e3) {
			System.err.println("Unkown network interface: " + cmd.getOptionValue("n") + " Exiting...");
			System.exit(1);
		} 
		if (serverAddress == null) {
			System.err.println("no IPV4 Address found on Network Interface " + (cmd.hasOption("n") ? cmd.getOptionValue("n") : "eth0"));
			System.exit(1);
		}
    	String serverAddressString = serverAddress.toString().replace("/","");
    	//print the IP-address if found
    	String serverPortString = (cmd.hasOption("p") ? cmd.getOptionValue("p"): "8000");
    	if (!serverPortString.matches("^[1-9][0-9]{0,5}$")){
    		System.err.println("port must be between 1 and 99999");
    		System.exit(0);
    	}
    	System.out.println("Starting server at " + serverAddressString + " port: " + serverPortString);
        StatServer.startServer("http://" + serverAddressString + ":" + serverPortString + "/gossipStatServer");;
	}

}
