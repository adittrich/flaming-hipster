package gossip.stat.client;

import gossip.stat.client.soap.StatServerService;
import gossip.stat.tools.Util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

public class Main {

	public static void main(String[] args) {

		// create Options Object
		Options options = new Options();

		// specify options
		options.addOption("s", true, "StatServer Address. Default ???");
		options.addOption("p", true, "base port for client. Default 9000");
		options.addOption("c", true,
				"Number of clients. If more than one specified, will try to use base port++ as ports. Default 1");
		options.addOption("i", true,
				"bootstrap client, gives the first simulated client an existing client in the network. Default none");
		options.addOption("t", true, "Test scenario. Default static");
		options.addOption("o", true,
				"start in output mode instead. Argument is name of output file. Default ./outputCyclon");
		options.addOption("q", true, "StatServer port. Default 8000");
		options.addOption("h", false, "Display this message");
		options.addOption("n", true, "Use this networkInterface. Default wlan0");
		options.addOption("x", false, "Cleanup server data for next experiment. Has to be combined with opton -o.");
		options.addOption("restart", true,
				"List of favoured neighbours for restart. Default empty (no restart). Format: [node, ... , node]");
		options.addOption("period", true, "Period for restarts. Default socketTimeout.");
		options.addOption("num", true, "Number of chosen favoured neighbors. Default 1.");
		options.addOption("prob", true, "Propability to replace neighbor. Default 1. Format: x.xx");
		options.addOption("cache", true, "Cache size. Default 10.");
		options.addOption("m", true , "Message size. Default 2. Has to be smaller than cache size.");
		options.addOption("cyc", true, "Maximum cycles. Default 2000");

		// read Options from command line
		CommandLineParser parser = new PosixParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);
		} catch (ParseException e) {
			e.printStackTrace();
			System.exit(1);
			;
		}

		// automatically generate the help statement
		HelpFormatter formatter = new HelpFormatter();
		if (cmd.hasOption("h")) {
			formatter.printHelp("cyclonClient", options);
			System.exit(0);
		}
		// Initialize options for both modes
		int statServerPort = (cmd.hasOption("q") ? Integer.parseInt(cmd.getOptionValue("q")) : 8000);

		// run in analyzer mode
		if (cmd.hasOption("a")) {
			String fileName = ((cmd.getOptionValue("a").equals("") || cmd.getOptionValue("o") == null) ? "outputCyclon"
					: cmd.getOptionValue("o"));
			try {
				InetAddress statServerAddress = InetAddress
						.getByName(cmd.hasOption("s") ? cmd.getOptionValue("s") : "");
				StatServerService _s = new StatServerService(new URL("http://" + statServerAddress.getHostName() + ":"
						+ statServerPort + "/gossipStatServer?wsdl"), new QName("http://server.stat.gossip/",
						"StatServerService"));
				gossip.stat.client.soap.StatServer s = _s.getStatServerPort();
				// TODO read in Values
				// TODO analyze and create output file
				System.exit(0);
			} catch (UnknownHostException e) {
				System.err.println("Unkown statistics server host: " + cmd.getOptionValue("s") + " Exiting.");
				System.exit(1);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// run output mode
		if (cmd.hasOption("o")) {
			String fileName = ((cmd.getOptionValue("o").equals("") || cmd.getOptionValue("o") == null) ? "outputCyclon"
					: cmd.getOptionValue("o"));
			try {
				InetAddress statServerAddress = InetAddress
						.getByName(cmd.hasOption("s") ? cmd.getOptionValue("s") : "");
				StatServerService _s = new StatServerService(new URL("http://" + statServerAddress.getHostName() + ":"
						+ statServerPort + "/gossipStatServer?wsdl"), new QName("http://server.stat.gossip/",
						"StatServerService"));
				gossip.stat.client.soap.StatServer s = _s.getStatServerPort();
				s.writeResults(fileName);
				System.out.println("XML Output written to " + fileName + ".gexf and physical topology to " + fileName
						+ ".topo.gexf");
				System.out.println("webservice counter = " + s.getCounter());
				System.out.println("lost messages = " + s.getLostPackagesCounter());
				System.out.println("nodes registered: " + s.getNodeNumber());
				// System.out.println("lost messages detail = ");
				// System.out.println(s.getWaitingMessages());
				if (cmd.hasOption("x")) {
					s.cleanup();
					System.out.println("Server cleaned up for next experiment.");
				}
				System.exit(0);
			} catch (UnknownHostException e) {
				System.err.println("Unkown statistics server host: " + cmd.getOptionValue("s") + " Exiting.");
				System.exit(1);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			// Initialize options for normal mode
			String networkInterfaceName = (cmd.hasOption("n") ? cmd.getOptionValue("n") : "wlan0");
			InetAddress networkInterfaceIP = null;
			try {
				networkInterfaceIP = Util.getLocalAddressbyName(networkInterfaceName);
			} catch (SocketException e3) {
				System.err.println("Unkown network Interface: " + cmd.getOptionValue("n") + " Exiting.");
				System.exit(1);
			}
			int basePort = (cmd.hasOption("p") ? Integer.parseInt(cmd.getOptionValue("p")) : 9000);
			int maxClients = (cmd.hasOption("c") ? Integer.parseInt(cmd.getOptionValue("c")) : 1);
			int maxCycles = (cmd.hasOption("cyc") ? Integer.parseInt(cmd.getOptionValue("cyc")) : 2000);
			boolean seed = !cmd.hasOption("i");
			int cache_size = (cmd.hasOption("cache") ? Integer.parseInt(cmd.getOptionValue("cache")) : 10);
			int message_size = (cmd.hasOption("m") ? Integer.parseInt(cmd.getOptionValue("m")) : 2);
			if(message_size > cache_size){
				System.err.println("Message size " + message_size + " bigger than cache size " + cache_size + " . Exiting.");
				System.exit(1);
			}
			int period = (cmd.hasOption("period") ? Integer.parseInt(cmd.getOptionValue("period")) : -1);
			int num = (cmd.hasOption("num") ? Integer.parseInt(cmd.getOptionValue("num")) : -1);
			int prob = (cmd.hasOption("prob") ? Integer.parseInt(cmd.getOptionValue("prob")) : -1);
			List<Neighbor> fav_list = new ArrayList<Neighbor>();
			if (cmd.hasOption("restart")) {
				String[] fav_list_strings = cmd.getOptionValue("restart").replace("[", "").replace("]", "").trim().split(",");
				for (int i = 0; i < fav_list_strings.length; i++) {
					try {
						InetAddress ip = InetAddress.getByName(fav_list_strings[i] + "-" + networkInterfaceName);
						Neighbor n = new Neighbor(ip, basePort);
						int count = 1;
						for(int j = 0; j < fav_list.size(); j++){
							if (fav_list.get(j).samePeer(n)) {
								n = new Neighbor(ip, fav_list.get(j).getPort()+1);
								j = -1;
								count++;	
							}
						}
						if(count <= maxClients){
							fav_list.add(n);
						}
					} catch (UnknownHostException e) {
						System.err.println("Unkown Host: " + e + " Exiting.");
						System.exit(1);
					}
				}
			}
			InetAddress seedIP = null;
			InetAddress statServerAddress = null;
			if (cmd.hasOption("s")) {
				try {
					statServerAddress = InetAddress.getByName(cmd.getOptionValue("s"));
					System.out.println("trying to use " + statServerAddress.getHostName() + " Port: " + statServerPort
							+ " as statServer");
				} catch (UnknownHostException e) {
					System.err.println("Unkown statistics server host: " + cmd.getOptionValue("c") + " Exiting.");
					System.exit(1);
				}
			}
			if (!seed)
				try {
					seedIP = InetAddress.getByName(cmd.getOptionValue("i"));
				} catch (UnknownHostException e1) {
					System.err.println("Unkown seed host: " + cmd.getOptionValue("c") + " Exiting.");
					System.exit(1);
				}

			// set System.out to file for debug purposes

			try {
				File path = new File(System.getProperty("user.home") + "/consoleOut");
				String filename = "CyclonClientConsoleOut_" + networkInterfaceIP.toString().substring(1) + ".log";
				File file = new File(path, filename);
				if (!file.exists()) {
					path.mkdirs();
					file.createNewFile();
				} else {
					file.delete();
					file.createNewFile();
				}
				System.out.println("printing output to " + file.getAbsolutePath());
				System.setOut(new PrintStream(new FileOutputStream(file)));
				System.setErr(System.out);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				System.exit(0);
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(0);
			}
			// run normal mode
			// start test scenario. Default static
			try {
				if (cmd.hasOption("t")) {
					if (cmd.getOptionValue("t").equals("static")) {
						CyclonStatic.runCyclon(basePort, maxClients, seed, seedIP, statServerAddress, statServerPort,
								networkInterfaceIP, fav_list, period, num, prob, cache_size, message_size, maxCycles);
					}
					if (cmd.getOptionValue("t").equals("churn")) {
						CyclonChurn.runCyclon(basePort, maxClients, seed, seedIP, statServerAddress, statServerPort,
								networkInterfaceIP, fav_list, period, num, prob, cache_size, message_size, maxCycles);
					}
				} else {
					CyclonStatic.runCyclon(basePort, maxClients, seed, seedIP, statServerAddress, statServerPort,
					networkInterfaceIP, fav_list, period, num, prob, cache_size, message_size, maxCycles);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

}
