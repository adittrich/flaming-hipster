package gossip.stat.analyzer;

import gossip.stat.server.Graph;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Scanner;

public class Main {

	public Main() {
		// TODO Auto-generated constructor stub
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		Graph physicalTopo = new Graph();
		Graph topology = new Graph();
		String outputfile = "";
		
		//boolean exit = false;
		
		Scanner in = new Scanner(System.in);
		boolean nextStep = false;
		while(!nextStep) {
			System.out.println("");
			System.out.println("Enter path to physical topology:");
			String s = in.nextLine();
			File fileName = new File(s);
			if (!fileName.isFile()) System.out.println("File does not exist.");
			else {
				System.out.println("Reading file: " + fileName.getName());
				physicalTopo.readAlternateResults(fileName);
				System.out.println("Reading finished.");
				nextStep=true;
			}
		}
		nextStep=false;
		while(!nextStep) {
			System.out.println("");
			System.out.println("Enter path to virtual topology:");
			String s = in.nextLine();
			File fileName = new File(s);
			if (!fileName.isFile()) System.out.println("File does not exist.");
			else {
				System.out.println("Reading file: " + fileName.getName());
				topology.readAlternateResults(fileName);
				System.out.println("Reading finished.");
				nextStep=true;
			}
		}
		nextStep=false;
		while(!nextStep) {
			System.out.println("");
			System.out.println("Enter filename prefix for the output:");
			String s = in.nextLine();
			if(s.isEmpty()) System.out.println("no filename given. Please try again");
			else {
				outputfile = s;
				System.out.println("Outputfile prefix set to: " + s);
				nextStep=true;
			}
		}
		nextStep=false;
		while(!nextStep){
			System.out.println("");
			System.out.println("Enter calculation mode:");
			String s = in.nextLine();
			if(s.startsWith("exit")){
				System.out.println("exiting...");
				System.exit(0);
			} else if (s.startsWith("probs")){
				System.out.println("set interval start: ");
				s = in.nextLine();
				int intervalStart = Integer.parseInt(s);
				System.out.println("set interval end:");
				s=in.nextLine();
				int intervalEnd = Integer.parseInt(s);
				outputfile += ".probs.csv";
				System.out.println("results will be written to " + outputfile);
				try {
					Writer output = new FileWriter(outputfile);
					output.write(physicalTopo.getAnalytics(intervalStart, intervalEnd, topology, physicalTopo));
					output.close();
				} catch(IOException e) {
					e.printStackTrace();
				}
				System.out.println("Finished!");
				
				
			}else {
				System.out.println("Unkown calculation mode. Known modes are:");
				System.out.println("probs - calculate probability for a link to exist within a timeframe depending on phisicyl hops.");
				System.out.println("exit - exits the program.");
			}
		}
	}
}
