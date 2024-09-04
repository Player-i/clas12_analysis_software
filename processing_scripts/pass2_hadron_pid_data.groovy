/*
 * author Timothy B. Hayward
 * 
 * SIDIS hadron pid studies
 */

// import CLAS12 physics classes
import org.jlab.io.hipo.*;
import org.jlab.io.base.DataEvent;
import org.jlab.clas.physics.*;
import org.jlab.clas12.physics.*;

// import from hayward_coatjava_extensions
import extended_kinematic_fitters.*; 
import analyzers.*;

// filetype for gathering files in directory
import groovy.io.FileType;

public static double phi_calculation (double x, double y) {
	// tracks are given with Cartesian values and so must be converted to cylindrical
	double phi = Math.toDegrees(Math.atan2(x,y));
	phi = phi - 90;
	if (phi < 0) {
		phi = 360 + phi;
	}
	phi = 360 - phi;
	return phi;	
}

public static double theta_calculation (double x, double y, double z) {
	// convert cartesian coordinates to polar angle
	double r = Math.pow(Math.pow(x,2)+Math.pow(y,2)+Math.pow(z,2),0.5);
	return (double) (180/Math.PI)*Math.acos(z/r);
}

public static void main(String[] args) {

	// Start time
	long startTime = System.currentTimeMillis();

	// ~~~~~~~~~~~~~~~~ set up input paramaeters ~~~~~~~~~~~~~~~~ //

	// Check if an argument is provided
	if (!args) {
	    // Print an error message and exit the program if the input directory is not specified
	    println("ERROR: Please enter a hipo file directory as the first argument");
	    System.exit(0);
	}
	// If the input directory is provided, iterate through each file recursively
	def hipo_list = []
	(args[0] as File).eachFileRecurse(FileType.FILES) 
		{ if (it.name.endsWith('.hipo')) hipo_list << it }

	// Set the PDG PID for p1 based on the provided 2nd argument or default to 211 (pi+)
	String p1_Str = args.length < 2 ? "211" : args[1];
	if (args.length < 2) println("WARNING: Specify a PDG PID for p1! Set to pi+ (211).");
	println("Set p1 PID = $p1_Str");
	int p1_int = p1_Str.toInteger(); // Convert p1_Str to integer

	// Set the output file name based on the provided 3rd argument or use the default name
	String output_file = args.length < 3 ? "hadron_dummy_out.txt" : args[2];
	if (args.length < 3) 
	    println("WARNING: Specify an output file name. Set to \"hadron_dummy_out.txt\".");
	File file = new File(output_file);
	file.delete();
	BufferedWriter writer = new BufferedWriter(new FileWriter(file));

	// Set the number of files to process based on the provided 4th argument
	// use the size of the hipo_list if no argument provided
	int n_files = args.length < 4 || Integer.parseInt(args[3]) > hipo_list.size()
	    ? hipo_list.size() : Integer.parseInt(args[3]);
	if (args.length < 4 || Integer.parseInt(args[3]) > hipo_list.size()) {
	    // Print warnings and information if the number of files is not specified or too large
	    println("WARNING: Number of files not specified or number too large.")
	    println("Setting # of files to be equal to number of files in the directory.");
	    println("There are $hipo_list.size files.");
	}

	int hadron_pair_counts = 0;
	GenericKinematicFitter research_fitter = new analysis_fitter(10.6041);
	EventFilter filter = new EventFilter("11:"+p1_Str+":"+":X+:X-:Xn");

	// create a StringBuilder for accumulating lines
	StringBuilder batchLines = new StringBuilder();

	int num_events = 0;
	int max_lines = 100;
	int lineCount = 0;
	int numLines = 0;
	for (current_file in 0..<n_files) {
		// limit to a certain number of files defined by n_files
		println("\n Opening file "+Integer.toString(current_file+1)
			+" out of "+n_files+".\n"); 
		
		HipoDataSource reader = new HipoDataSource();
		reader.open(hipo_list[current_file]); // open next hipo file
		HipoDataEvent event = reader.getNextEvent(); 

		while(reader.hasEvent()){
			++num_events; 
			if (num_events%100000 == 0) { 
				print("processed: "+num_events+" events. ");
			}

			// get run and event numbers
			event = reader.getNextEvent();
		    int runnum = event.getBank("RUN::config").getInt('run',0);
		    int evnum = event.getBank("RUN::config").getInt('event',0);

		    PhysicsEvent research_Event = research_fitter.getPhysicsEvent(event);

			if (filter.isValid(research_Event)) {

				HipoDataBank recBank = (HipoDataBank) event.getBank("REC::Particle");

				int num_p1 = research_Event.countByPid(p1_Str.toInteger()); 

				for (int current_p1 = 0; current_p1 < num_p1; current_p1++) {

					Particle exp_e = research_Event.getParticleByPid(11,0);
					Particle exp_p1 = research_Event.getParticleByPid(p1_Str.toInteger(),current_p1);

					BeamEnergy Eb = new BeamEnergy(runnum, false);
					Hadron variables = new Hadron(event, research_Event, 
						p1_Str.toInteger(), current_p1, Eb.Eb());

					if (variables.channel_test(variables)) {

						// lab kinematics data
						double p_p;
						double p_theta;
						double p_phi;
						double vz_p = variables.vz_p();

						float beta, chi2pid;
						for(int current_part = 0; current_part < recBank.rows(); current_part++) {
							if (recBank.getInt("pid", current_part) == p1_Str.toInteger()) {
								beta = recBank.getFloat("beta", current_part);
			            		chi2pid = recBank.getFloat("chi2pid", current_part);
			            		float px = recBank.getFloat("px", current_part);
						        float py = recBank.getFloat("py", current_part);
						        float pz = recBank.getFloat("pz", current_part);
						        p_p = Math.sqrt(Math.pow(px,2)+Math.pow(py,2)+Math.pow(pz,2));
						        p_theta = theta_calculation(px, py, pz);
						        p_phi = phi_calculation(px, py);
							}
						}

						// Use a StringBuilder to append all data in a single call
		                StringBuilder line = new StringBuilder();
						line.append(p_p).append(" ")
							.append(p_theta).append(" ")
							.append(p_phi).append(" ")
							.append(beta).append(" ")
							.append(chi2pid).append("\n");
						numLines++;

						// Append the line to the batchLines StringBuilder
		                batchLines.append(line.toString());
		                lineCount++; // Increment the line count

		                // If the line count reaches 100, write to the file and reset
		                if (lineCount >= max_lines) {
		                    file.append(batchLines.toString());
		                    batchLines.setLength(0);
		                    lineCount = 0;
		                }
					}
				}
			}
		reader.close();
		}

		// Write any remaining lines in the batchLines StringBuilder to the file
		if (batchLines.length() > 0) {
		    file.append(batchLines.toString());
		    batchLines.setLength(0);
		}

		println(); println();
		print("1: p_p, 2: p_theta, 3: p_phi, ");
		print("4: beta, 5: chi2pid.\n");

		println(); println();
		println("Set p1 PID = "+p1_Str+"\n");
		println("output file is: "+file);
		println();
	}

}