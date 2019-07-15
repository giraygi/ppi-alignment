package typed.ppi;

import java.io.File;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import akka.actor.TypedActor;
import akka.actor.TypedProps;
import akka.dispatch.OnComplete;
import akka.dispatch.Recover;
import akka.japi.Creator;
import akka.routing.TailChoppingGroup;
import akka.util.Timeout;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

public class CLIPPI {
	
	static String databaseAddress ="neo4j-community-3.5.6" ;
	static Timeout timeout = new Timeout(Duration.create(360, "seconds"));
	int tolerance = 100;
	int finalMappingFactor = 100;
	double noofPercentileSteps = 10.0;
	int populationSize = 10;
	
	private static final Logger log = Logger.getLogger(CLIPPI.class.getName());
	 private String[] args = null;
	 private Options options = new Options();

	 public CLIPPI(String[] args) {

	  this.args = args;

	  options.addOption("h", "help", false, "show help.");
	  
	  options.addOption("n1", "nodes1", true, "nodes file of the first network.");
	  options.addOption("n2", "nodes2", true, "nodes file of the second network.");
	  options.addOption("s", "var", true, "similarity file between networks.");
	  options.addOption("i1", "interactions1", true, "interactions file of the first network.");
	  options.addOption("i2", "interactions2", true, "interactions file of the second network.");
	  options.addOption("a1", "annotations1", true, "annotations file of the first network.");
	  options.addOption("a2", "annotations2", true, "annotations file of the second network.");
	  
	  options.addOption("c", "create", false, "create networks, similarity and annotations from scratch");
	  options.addOption("po", "powers", false, "compute powers");
	  options.addOption("r", "removealignment", false, "remove all previous alignments, logs and files");
	  options.addOption("i", "interactivealignment", false, "Execute the interactive alignment phase");
	  options.addOption("epp", "executepostprocessing", false, "Execute post processing phases of alignments with random search.");
	   
	  options.addOption("t", "tolerance", true, "No of missing mappings allowed for the alignment.");
	  options.addOption("fmf", "finalmappingfactor", true, "No of Mappings added in each Cycle of the final stage of post processing.");
	  options.addOption("p", "path", true, "Directory of alignments");
	  options.addOption("db", "dbaddress", true, "Database address of neo4j .");
	  options.addOption("e", "extension", true, "File extension for the alignments to be stored/loaded .");
	  options.addOption("l", "label", true, "Initial tag for the alignments to be stored.");
	  options.addOption("g", "greedy", false, "Greedy execution of alignments.");
	  options.addOption("ch", "chains", false, "Chains are executed in the initial phase of alignment.");
	  options.addOption("d", "deletealignments", false, "Previous alignments are deleted.");
	  options.addOption("lo", "loadalignments", false, "Previous alignments are loaded from specified directory.");
	  options.addOption("sv", "savealignments", false, "Resulting alignments and markedqueries are saved into specified directory.");
	  options.addOption("svs", "savestatistics", false, "Alignments in the specified directory are aggregated for statistics.");
	  
	  options.addOption("cc", "centralitiescommunities", false, "Compute Several Centrality and Community approaches for the networks.");
	  options.addOption("nps", "noofpercentilesteps", true, "Number of Percentile Steps to be calculated for the Meta Data.");
	  options.addOption("ps", "populationsize", true, "Population Size to be improved in post processing.");

	 }

	 public void parse() {
	  CommandLineParser parser = new DefaultParser();

	  CommandLine cmd = null;
	  try {
	   cmd = parser.parse(options, args);

	   if (cmd.hasOption("h"))
	    help();

	   if (cmd.hasOption("n1")) {
	    log.log(Level.INFO, "Using cli argument -n1=" + cmd.getOptionValue("n1"));
	    File temp = new File(cmd.getOptionValue("n1"));
	  if( temp.isFile()&&temp.canRead()&&temp.canWrite())
		  System.out.println(cmd.getOptionValue("n1")+" is a proper file for i/o");
	  else
	  {
		  System.err.println(cmd.getOptionValue("n1")+" may not be a proper file for i/o");
		  help();
	  }
	   } else {
	    log.log(Level.SEVERE, "MIssing n1 option");
	    help();
	   }
	   
	   if (cmd.hasOption("n2")) {
		    log.log(Level.INFO, "Using cli argument -n2=" + cmd.getOptionValue("n2"));      
		    File temp = new File(cmd.getOptionValue("n2"));
			  if( temp.isFile()&&temp.canRead()&&temp.canWrite())
				  System.out.println(cmd.getOptionValue("n2")+" is a proper file for i/o");
			  else
			  {
				  System.err.println(cmd.getOptionValue("n2")+" may not be a proper file for i/o");
				  help();
			  }
		    
		   } else {
		    log.log(Level.SEVERE, "MIssing n2 option");
		    help();
		   }
	   
	   if (cmd.hasOption("i1")) {
		    log.log(Level.INFO, "Using cli argument -i1=" + cmd.getOptionValue("i1"));
		    		    
		    File temp = new File(cmd.getOptionValue("i1"));
			  if( temp.isFile()&&temp.canRead()&&temp.canWrite())
				  System.out.println(cmd.getOptionValue("i1")+" is a proper file for i/o");
			  else
			  {
				  System.err.println(cmd.getOptionValue("i1")+" may not be a proper file for i/o");
				  help();
			  }    
		    
		   } else {
		    log.log(Level.SEVERE, "MIssing i1 option");
		    help();
		   }
		   
	 if (cmd.hasOption("i2")) {
			 log.log(Level.INFO, "Using cli argument -i2=" + cmd.getOptionValue("i2"));
			   		 
			    File temp = new File(cmd.getOptionValue("i2"));
				  if( temp.isFile()&&temp.canRead()&&temp.canWrite())
					  System.out.println(cmd.getOptionValue("i2")+" is a proper file for i/o");
				  else
				  {
					  System.err.println(cmd.getOptionValue("i2")+" may not be a proper file for i/o");
					  help();
				  }
			 
			   } else {
			    log.log(Level.SEVERE, "MIssing i2 option");
			    help();
			   }
		   
		 if (cmd.hasOption("a1")) {
			    log.log(Level.INFO, "Using cli argument -a1=" + cmd.getOptionValue("a1"));
			        
			    File temp = new File(cmd.getOptionValue("a1"));
				  if( temp.isFile()&&temp.canRead()&&temp.canWrite())
					  System.out.println(cmd.getOptionValue("a1")+" is a proper file for i/o");
				  else
				  {
					  System.err.println(cmd.getOptionValue("a1")+" may not be a proper file for i/o");
					  help();
				  }
			    
			   } else {
			    log.log(Level.SEVERE, "MIssing a1 option");
			    help();
			   }
			   
		if (cmd.hasOption("a2")) {
				    log.log(Level.INFO, "Using cli argument -a2=" + cmd.getOptionValue("a2"));
				      
				    File temp = new File(cmd.getOptionValue("a2"));
					  if( temp.isFile()&&temp.canRead()&&temp.canWrite())
						  System.out.println(cmd.getOptionValue("a2")+" is a proper file for i/o");
					  else
					  {
						  System.err.println(cmd.getOptionValue("a2")+" may not be a proper file for i/o");
						  help();
					  }
				    
			} else {
				    log.log(Level.SEVERE, "MIssing a2 option");
				    help();
				   }
		
		 if (cmd.hasOption("s")) {
			    log.log(Level.INFO, "Using cli argument -s=" + cmd.getOptionValue("s"));
			    	    
			    File temp = new File(cmd.getOptionValue("s"));
				  if( temp.isFile()&&temp.canRead()&&temp.canWrite())
					  System.out.println(cmd.getOptionValue("s")+" is a proper file for i/o");
				  else
				  {
					  System.err.println(cmd.getOptionValue("s")+" may not be a proper file for i/o");
					  help();
				  }
			    
			   } else {
			    log.log(Level.SEVERE, "MIssing s option");
			    help();
			   }
		 
		 if (cmd.hasOption("c")) {
			 log.log(Level.INFO, "Using cli argument -c");
			 System.out.println("All Network data is going to be deleted and created from scratch.");
		 }
			 
		 if (cmd.hasOption("po")) {
			 log.log(Level.INFO, "Using cli argument -po");
			 System.out.println("Powers are going to be computed.");
		 }
			 
		 if (cmd.hasOption("r")) {
			 log.log(Level.INFO, "Using cli argument -r");
			 System.out.println("Previous alignments, logs and files are going to be removed.");
		 }
			 
		 if (cmd.hasOption("i")) {
			 log.log(Level.INFO, "Using cli argument -i");
			 System.out.println("Interactive alignment phase will be executed.");
		 }
			 
		 if (cmd.hasOption("epp")) {
			 log.log(Level.INFO, "Using cli argument -epp");
			 System.out.println("Post Processing Phase will be executed with Random Search.");
		 }
		 
		 if (cmd.hasOption("t")) {
			    log.log(Level.INFO, "Using cli argument -t=" + cmd.getOptionValue("t"));
			        
				try {
					if(Integer.parseInt(cmd.getOptionValue("t"))>0) {
						tolerance = Integer.parseInt(cmd.getOptionValue("t"));
						System.out.println("Tolerance number of alignments from argument  is "+tolerance);
					} else
						System.out.println("Tolerance number should be a positive number. Switching to the default value "+tolerance);
				} catch (NumberFormatException e) {
					System.err.println("Number Format Exception in Tolerance Number. Switching to the default value "+tolerance);
				} catch (ArrayIndexOutOfBoundsException aioobe) {
					System.err.println("Tolerance number is not entered. Switching to the default value "+tolerance);
				}		    	    
			    
			   } 	 
		 
		 if (cmd.hasOption("fmf")) {
			    log.log(Level.INFO, "Using cli argument -fmf=" + cmd.getOptionValue("fmf"));       
				try {
					if(Integer.parseInt(cmd.getOptionValue("fmf"))>0) {
						tolerance = Integer.parseInt(cmd.getOptionValue("fmf"));
						System.out.println("Final Mapping Factor number of alignments from argument  is "+finalMappingFactor);
					} else
						System.out.println("Final Mapping Factor number should be a positive number. Switching to the default value "+finalMappingFactor);
				} catch (NumberFormatException e) {
					System.err.println("Number Format Exception in Final Mapping Factor Number. Switching to the default value "+finalMappingFactor);
				} catch (ArrayIndexOutOfBoundsException aioobe) {
					System.err.println("Final Mapping Factor number is not entered. Switching to the default value "+finalMappingFactor);
				}		    	    
			    
			   } 
		  
		   if (cmd.hasOption("p")) {
			    log.log(Level.INFO, "Using cli argument -p=" + cmd.getOptionValue("p"));
			    File temp = new File(cmd.getOptionValue("p"));
			  if( temp.isDirectory()&&temp.canRead()&&temp.canWrite())
				  System.out.println(cmd.getOptionValue("p")+" is a proper directory for i/o");
			  else
			  {
				  System.err.println(cmd.getOptionValue("p")+" may not be a proper directory for i/o");
				  System.err.println("Attempting to create "+cmd.getOptionValue("p"));
				  			
					File outputDirectory = new File(cmd.getOptionValue("p"));
			        if (!outputDirectory.exists()) {
			            if (outputDirectory.mkdirs()) {
			                System.out.println("Directory "+cmd.getOptionValue("p")+" is created!");
			            } else {
			                System.out.println("Directory "+cmd.getOptionValue("p")+" could not be created");
			                help();
			            }
			        }
				  
			  }
			   } 
			  
			  if (cmd.hasOption("db")) {
				  log.log(Level.INFO, "Using cli argument -db=" + cmd.getOptionValue("db"));
				  System.out.println("Using cli argument -db=" + cmd.getOptionValue("db"));
			  }  
			  
			  if (cmd.hasOption("e")) {
				  log.log(Level.INFO, "Using cli argument -e=" + cmd.getOptionValue("e"));
				  System.out.println("Using cli argument -e=" + cmd.getOptionValue("e"));
			  }
			  
			  if (cmd.hasOption("l")) {
				  log.log(Level.INFO, "Using cli argument -l=" + cmd.getOptionValue("l"));
				  System.out.println("Using cli argument -l=" + cmd.getOptionValue("l"));
			  }
			  
			  if (cmd.hasOption("g")) {
				  log.log(Level.INFO, "Using cli argument -g");
				  System.out.println("Using cli argument -g");
			  }  
			  
			  if (cmd.hasOption("ch")) {
				  log.log(Level.INFO, "Using cli argument -ch");
				  System.out.println("Using cli argument -ch");
			  }  
			   
			   if (cmd.hasOption("d")) {
				   log.log(Level.INFO, "Using cli argument -d");
				   System.out.println("Previous alignments are going to be deleted.");
			   }
					 
			   
			   if (cmd.hasOption("lo")) {
				   log.log(Level.INFO, "Using cli argument -lo");
				   System.out.println("Previous alignments are going to be loaded from specified directory.");
			   }
					 
			   
			   if (cmd.hasOption("sv")) {
				   log.log(Level.INFO, "Using cli argument -sv");
				   System.out.println("Resulting alignments are going to be saved into specified directory.");
			   }
			   
			   if (cmd.hasOption("svs")) {
				   log.log(Level.INFO, "Using cli argument -svs");
				   System.out.println("Alignments in the specified directory are going to be aggregated for statistics.");
			   }			 
			   
			   if (cmd.hasOption("cc")) {
				   log.log(Level.INFO, "Using cli argument -cc");
				   System.out.println("Centralities and Communities are going to be computed for network characteristics.");
			   }
					 
			   	   
				 if (cmd.hasOption("nps")) {
					    log.log(Level.INFO, "Using cli argument -nps=" + cmd.getOptionValue("nps"));       
						try {
							if(Integer.parseInt(cmd.getOptionValue("nps"))>0) {
								tolerance = Integer.parseInt(cmd.getOptionValue("nps"));
								System.out.println("Number of Percentile Steps of alignments from argument  is "+noofPercentileSteps);
							} else
								System.out.println("Number of Percentile Steps should be a positive number. Switching to the default value "+noofPercentileSteps);
						} catch (NumberFormatException e) {
							System.err.println("Number Format Exception in Number of Percentile Steps. Switching to the default value "+noofPercentileSteps);
						} catch (ArrayIndexOutOfBoundsException aioobe) {
							System.err.println("Number of Percentile Steps is not entered. Switching to the default value "+noofPercentileSteps);
						}		    	    
					    
					   } 
				 	 
				 if (cmd.hasOption("ps")) {
					    log.log(Level.INFO, "Using cli argument -ps=" + cmd.getOptionValue("ps"));       
						try {
							if(Integer.parseInt(cmd.getOptionValue("ps"))>0) {
								tolerance = Integer.parseInt(cmd.getOptionValue("ps"));
								System.out.println("Population Size of alignments from argument  is "+populationSize);
							} else
								System.out.println("Population Size should be a positive number. Switching to the default value "+populationSize);
						} catch (NumberFormatException e) {
							System.err.println("Number Format Exception in Population Size. Switching to the default value "+populationSize);
						} catch (ArrayIndexOutOfBoundsException aioobe) {
							System.err.println("Population Size is not entered. Switching to the default value "+populationSize);
						}		    	    
					    
					   } 
		 
		
	  } catch (ParseException e) {
	   log.log(Level.SEVERE, "Failed to parse comand line properties", e);
	   help();
	  }
	  
	  
	  	databaseAddress = cmd.getOptionValue("db");	
		final AkkaSystem as = new AkkaSystem(1,cmd.getOptionValue("db"),100,20);
		if(cmd.hasOption("c"))
		{
			as.deleteAllNodesRelationships();
			as.createGraph(cmd.getOptionValue("n1"), cmd.getOptionValue("n2"), cmd.getOptionValue("s"), cmd.getOptionValue("i1"), cmd.getOptionValue("i2"), cmd.getOptionValue("a1"), cmd.getOptionValue("a2"));	
		}
		
		if(cmd.hasOption("po"))
		{
			as.computePowers();
		}
		
		if(cmd.hasOption("cc")){
			as.computePageRank(20, 0.85);
			as.computeBetweennessCentrality();
			as.computeClosenessCentrality();
			as.computeHarmonicCentrality();
			as.computeLabelPropagationCommunities(1);
			as.computeLouvainCommunities();
			as.computeClusterSimilarities("louvain", 5);
			as.computeClusterSimilarities("labelpropagation", 5);
		}
		
		as.csLouvainGO = as.sortSimilarClusters("louvain", "commonGO");
		as.csLouvainBitScore = as.sortSimilarClusters("louvain", "BitScore");	
		as.csLabelPropagationGO = as.sortSimilarClusters("labelpropagation", "commonGO");
		as.csLabelPropagationBitScore = as.sortSimilarClusters("labelpropagation", "BitScore");
		
		as.computeMetaData();
		as.computePowerMetaData();
		as.computeClusterMetaData();
		as.computeFunctionalMetaData();
			
		if (cmd.hasOption("r")) {
			as.removeLogFiles();
			as.removeAllMarks();
			as.removeAllQueries();
			as.removeAllAlignments();
		}	
		
		if(cmd.hasOption("lo")&&cmd.hasOption("p")&&cmd.hasOption("e")) {
			populationSize = 10;
			try {
					File file = new File(cmd.getOptionValue("p"));
					if (!file.isDirectory())
					   file = file.getParentFile();
					if (file.exists()){
						populationSize = as.initializePreviousAlignmentsFromFolder(1, cmd.getOptionValue("p"), cmd.getOptionValue("e"));
						System.out.println("Population size of previous alignments from folder  is "+populationSize);
					} else {
						try {
							if(Integer.parseInt(cmd.getOptionValue("ps"))>0) {
								populationSize = Integer.parseInt(cmd.getOptionValue("ps"));
								System.out.println("Population size of alignments from argument/live database  is "+populationSize);
							} 
							else
								System.out.println("Population size should be a positive number. Switching to the default value "+populationSize);							
						} catch (NumberFormatException e) {
							System.err.println("Number Format Exception in Population Size. Switching to the default value "+populationSize);
						} catch (ArrayIndexOutOfBoundsException aioobe) {
							System.err.println("Population Size is not entered. Switching to the default value "+populationSize);
						}
					}
			} catch (ArrayIndexOutOfBoundsException aioobe) {
				aioobe.printStackTrace();
			}	
			File f = new File(cmd.getOptionValue("p")+File.separator+cmd.getOptionValue("l")+".txt");
			if(f.exists()&&f.canRead())
				as.loadOldMarkedQueriesFromFileToDB(cmd.getOptionValue("p")+File.separator+cmd.getOptionValue("l")+".txt");
		}
		
		if(cmd.hasOption("lo"))
			as.loadOldMarkedQueriesFromDBToApplication();	
		
		if (cmd.hasOption("i")) {
			
			Aligner firstAligner = TypedActor.get(AkkaSystem.system2)
					.typedActorOf(new TypedProps<AlignerImpl>(Aligner.class, new Creator<AlignerImpl>() {
						/**
						 * 
						 */
						private static final long serialVersionUID = 3536927801179987634L;

						public AlignerImpl create() {
							return new AlignerImpl(as, 1);
						}
					}).withTimeout(timeout), "name");
			Aligner secondAligner = TypedActor.get(AkkaSystem.system2)
					.typedActorOf(new TypedProps<AlignerImpl>(Aligner.class, new Creator<AlignerImpl>() {
						/**
						 * 
						 */
						private static final long serialVersionUID = 3536927801179987634L;

						public AlignerImpl create() {
							return new AlignerImpl(as, 2);
						}
					}).withTimeout(timeout), "name2");
			Aligner thirdAligner = TypedActor.get(AkkaSystem.system2)
					.typedActorOf(new TypedProps<AlignerImpl>(Aligner.class, new Creator<AlignerImpl>() {
						/**
						 * 
						 */
						private static final long serialVersionUID = 3536927801179987634L;

						public AlignerImpl create() {
							return new AlignerImpl(as, 3);
						}
					}).withTimeout(timeout), "name3");
			Aligner fourthAligner = TypedActor.get(AkkaSystem.system2)
					.typedActorOf(new TypedProps<AlignerImpl>(Aligner.class, new Creator<AlignerImpl>() {
						/**
						 * 
						 */
						private static final long serialVersionUID = 3536927801179987634L;

						public AlignerImpl create() {
							return new AlignerImpl(as, 4);
						}
					}).withTimeout(timeout), "name4");
			Aligner fifthAligner = TypedActor.get(AkkaSystem.system2)
					.typedActorOf(new TypedProps<AlignerImpl>(Aligner.class, new Creator<AlignerImpl>() {
						/**
						 * 
						 */
						private static final long serialVersionUID = 3536927801179987634L;

						public AlignerImpl create() {
							return new AlignerImpl(as, 5);
						}
					}).withTimeout(timeout), "name5");
			Aligner sixthAligner = TypedActor.get(AkkaSystem.system2)
					.typedActorOf(new TypedProps<AlignerImpl>(Aligner.class, new Creator<AlignerImpl>() {
						/**
						 * 
						 */
						private static final long serialVersionUID = 3536927801179987634L;

						public AlignerImpl create() {
							return new AlignerImpl(as, 6);
						}
					}).withTimeout(timeout), "name6");
			Aligner seventhAligner = TypedActor.get(AkkaSystem.system2)
					.typedActorOf(new TypedProps<AlignerImpl>(Aligner.class, new Creator<AlignerImpl>() {
						/**
						 * 
						 */
						private static final long serialVersionUID = 3536927801179987634L;

						public AlignerImpl create() {
							return new AlignerImpl(as, 7);
						}
					}).withTimeout(timeout), "name7");
			Aligner eighthAligner = TypedActor.get(AkkaSystem.system2)
					.typedActorOf(new TypedProps<AlignerImpl>(Aligner.class, new Creator<AlignerImpl>() {
						/**
						 * 
						 */
						private static final long serialVersionUID = 3536927801179987634L;

						public AlignerImpl create() {
							return new AlignerImpl(as, 8);
						}
					}).withTimeout(timeout), "name8");
			Aligner ninthAligner = TypedActor.get(AkkaSystem.system2)
					.typedActorOf(new TypedProps<AlignerImpl>(Aligner.class, new Creator<AlignerImpl>() {
						/**
						 * 
						 */
						private static final long serialVersionUID = 3536927801179987634L;

						public AlignerImpl create() {
							return new AlignerImpl(as, 9);
						}
					}).withTimeout(timeout), "name9");
			Aligner tenthAligner = TypedActor.get(AkkaSystem.system2)
					.typedActorOf(new TypedProps<AlignerImpl>(Aligner.class, new Creator<AlignerImpl>() {
						/**
						 * 
						 */
						private static final long serialVersionUID = 3536927801179987634L;

						public AlignerImpl create() {
							return new AlignerImpl(as, 10);
						}
					}).withTimeout(timeout), "name10");
			as.routees.add(firstAligner);
			as.routees.add(secondAligner);
			as.routees.add(thirdAligner);
			as.routees.add(fourthAligner);
			as.routees.add(fifthAligner);
			as.routees.add(sixthAligner);
			as.routees.add(seventhAligner);
			as.routees.add(eighthAligner);
			as.routees.add(ninthAligner);
			as.routees.add(tenthAligner);
			as.loadOldMarkedQueriesFromDBToApplication();
			as.routeePaths.add(as.typed.getActorRefFor(firstAligner).path().toStringWithoutAddress());
			as.routeePaths.add(as.typed.getActorRefFor(secondAligner).path().toStringWithoutAddress());
			as.routeePaths.add(as.typed.getActorRefFor(thirdAligner).path().toStringWithoutAddress());
			as.routeePaths.add(as.typed.getActorRefFor(fourthAligner).path().toStringWithoutAddress());
			as.routeePaths.add(as.typed.getActorRefFor(fifthAligner).path().toStringWithoutAddress());
			as.routeePaths.add(as.typed.getActorRefFor(sixthAligner).path().toStringWithoutAddress());
			as.routeePaths.add(as.typed.getActorRefFor(seventhAligner).path().toStringWithoutAddress());
			as.routeePaths.add(as.typed.getActorRefFor(eighthAligner).path().toStringWithoutAddress());
			as.routeePaths.add(as.typed.getActorRefFor(ninthAligner).path().toStringWithoutAddress());
			as.routeePaths.add(as.typed.getActorRefFor(tenthAligner).path().toStringWithoutAddress());
			FiniteDuration within = FiniteDuration.create(10, TimeUnit.SECONDS);
			FiniteDuration interval = FiniteDuration.create(2000, TimeUnit.MILLISECONDS);
			AkkaSystem.router = AkkaSystem.system2
					.actorOf(new TailChoppingGroup(as.routeePaths, within, interval).props(), "router");
			
			
			Random rand1 = new Random();
			Random rand2 = new Random();
			Random rand3 = new Random();
			
			int  n = rand1.nextInt((int)Math.floor(as.averageCommonAnnotations));
			double s =  rand2.nextInt(4)*as.averageSimilarity/4;
			char p = (char) (rand3.nextInt(3)+2);
			
			Future<Boolean> f = firstAligner.alignCentralPowerNodesFromTop(n, s, p, 0.3,1000, '3');
			Future<Boolean> f3 = sixthAligner.alignAlternativeCentralNodesFromTop(n, s, 0.3, 1000, "pagerank", '3');
			Future<Boolean> f5 = eighthAligner.alignAlternativeCentralNodesFromTop(n, s, 0.3, 1000, "betweenness", '3');
			Future<Boolean> f7 = ninthAligner.alignAlternativeCentralNodesFromTop(n, s, 0.3, 1000, "harmonic", '3');
			Future<Boolean> f9 = tenthAligner.alignAlternativeCentralNodesFromTop(n, s, 0.3, 1000, "closeness", '3');	
			
			try {
				
				if(cmd.hasOption("ch")) {	
					System.out.println("Chain Mode is Activated!!!");
					as.descendParameterValuesOfChain(f, firstAligner, (int)Math.floor(as.averageCommonAnnotations), as.averageSimilarity, (int)Math.ceil(as.averageSimilarity/as.minSimilarity),true);
					as.descendParameterValuesOfChain(f3, sixthAligner, (int)Math.floor(as.averageCommonAnnotations), as.averageSimilarity, (int)Math.ceil(as.averageSimilarity/as.minSimilarity),true);
					as.descendParameterValuesOfChain(f5, eighthAligner, (int)Math.floor(as.averageCommonAnnotations), as.averageSimilarity, (int)Math.ceil(as.averageSimilarity/as.minSimilarity),true);
					as.descendParameterValuesOfChain(f7, ninthAligner, (int)Math.floor(as.averageCommonAnnotations), as.averageSimilarity, (int)Math.ceil(as.averageSimilarity/as.minSimilarity),true);
					as.descendParameterValuesOfChain(f9, tenthAligner, (int)Math.floor(as.averageCommonAnnotations), as.averageSimilarity, (int)Math.ceil(as.averageSimilarity/as.minSimilarity),true);
				}
				
				if(cmd.hasOption("g")) {
					System.out.println("Greedy Mode is Activated!!!");
					Future<Boolean> f2 = f.andThen(new OnComplete<Boolean>() {
						public void onComplete(Throwable failure, Boolean success) {
							if (success && failure == null) {
								System.out.println("SONRAKİ");
								firstAligner.increaseECByAddingPair(4, 0.0, '3');
								firstAligner.increaseECByAddingPair(3, 0.0, '3');
								firstAligner.increaseECByAddingPair(2, 0.0, '3');
								firstAligner.increaseECByAddingPair(1, 50.0, '3');
								firstAligner.increaseECByAddingPair(0, 50.0, '3');
								firstAligner.increaseECByAddingPair(0, 0, '3');
							} else if (failure != null)
								System.out.println("KAMİLLEEEER" + failure.getMessage());
						}
					}, AkkaSystem.system2.dispatcher()).recover(new Recover<Boolean>() {
						public Boolean recover(Throwable problem) throws Throwable {
							if (problem instanceof org.neo4j.driver.v1.exceptions.TransientException
									|| problem instanceof Exception || problem != null) {
								System.out.println("RECOVER ETTİM GARİ");
								firstAligner.increaseECByAddingPair(4, 0.0, '3');
								firstAligner.increaseECByAddingPair(3, 0.0, '3');
								firstAligner.increaseECByAddingPair(2, 0.0, '3');
								firstAligner.increaseECByAddingPair(1, 50.0, '3');
								firstAligner.increaseECByAddingPair(0, 50.0, '3');
								firstAligner.increaseECByAddingPair(0, 0, '3');
								return true;
							}
							return false;
						}
					}, AkkaSystem.system2.dispatcher());
					
					Future<Boolean> f4 = f3.andThen(new OnComplete<Boolean>() {
						public void onComplete(Throwable failure, Boolean success) {
							if (success && failure == null) {
								System.out.println("SONRAKİ");
								sixthAligner.increaseECByAddingPair(4, 0.0, '3');
								sixthAligner.increaseECByAddingPair(3, 0.0, '3');
								sixthAligner.increaseECByAddingPair(2, 0.0, '3');
								sixthAligner.increaseECByAddingPair(1, 50.0, '3');
								sixthAligner.increaseECByAddingPair(0, 50.0, '3');
								sixthAligner.increaseECByAddingPair(0, 0, '3');
							} else if (failure != null)
								System.out.println("KAMİLLEEEER" + failure.getMessage());
						}
					}, AkkaSystem.system2.dispatcher()).recover(new Recover<Boolean>() {
						public Boolean recover(Throwable problem) throws Throwable {
							if (problem instanceof org.neo4j.driver.v1.exceptions.TransientException
									|| problem instanceof Exception || problem != null) {
								System.out.println("RECOVER ETTİM GARİ");
								sixthAligner.increaseECByAddingPair(4, 0.0, '3');
								sixthAligner.increaseECByAddingPair(3, 0.0, '3');
								sixthAligner.increaseECByAddingPair(2, 0.0, '3');
								sixthAligner.increaseECByAddingPair(1, 50.0, '3');
								sixthAligner.increaseECByAddingPair(0, 50.0, '3');
								sixthAligner.increaseECByAddingPair(0, 0, '3');
								return true;
							}
							return false;
						}
					}, AkkaSystem.system2.dispatcher());
					
					Future<Boolean> f6 = f5.andThen(new OnComplete<Boolean>() {
						public void onComplete(Throwable failure, Boolean success) {
							if (success && failure == null) {
								System.out.println("SONRAKİ");
								eighthAligner.increaseECByAddingPair(4, 0.0, '3');
								eighthAligner.increaseECByAddingPair(3, 0.0, '3');
								eighthAligner.increaseECByAddingPair(2, 0.0, '3');
								eighthAligner.increaseECByAddingPair(1, 50.0, '3');
								eighthAligner.increaseECByAddingPair(0, 50.0, '3');
								eighthAligner.increaseECByAddingPair(0, 0, '3');
							} else if (failure != null)
								System.out.println("KAMİLLEEEER" + failure.getMessage());
						}
					}, AkkaSystem.system2.dispatcher()).recover(new Recover<Boolean>() {
						public Boolean recover(Throwable problem) throws Throwable {
							//  		if (problem instanceof Exception)
							if (problem instanceof org.neo4j.driver.v1.exceptions.TransientException
									|| problem instanceof Exception || problem != null) {
								System.out.println("RECOVER ETTİM GARİ");
								eighthAligner.increaseECByAddingPair(4, 0.0, '3');
								eighthAligner.increaseECByAddingPair(3, 0.0, '3');
								eighthAligner.increaseECByAddingPair(2, 0.0, '3');
								eighthAligner.increaseECByAddingPair(1, 50.0, '3');
								eighthAligner.increaseECByAddingPair(0, 50.0, '3');
								eighthAligner.increaseECByAddingPair(0, 0, '3');
								return true;
							}
							return false;
						}
					}, AkkaSystem.system2.dispatcher());
					
					Future<Boolean> f8 = f7.andThen(new OnComplete<Boolean>() {
						public void onComplete(Throwable failure, Boolean success) {
							if (success && failure == null) {
								System.out.println("SONRAKİ");
								ninthAligner.increaseECByAddingPair(4, 0.0, '3');
								ninthAligner.increaseECByAddingPair(3, 0.0, '3');
								ninthAligner.increaseECByAddingPair(2, 0.0, '3');
								ninthAligner.increaseECByAddingPair(1, 50.0, '3');
								ninthAligner.increaseECByAddingPair(0, 50.0, '3');
								ninthAligner.increaseECByAddingPair(0, 0, '3');
							} else if (failure != null)
								System.out.println("KAMİLLEEEER" + failure.getMessage());
						}
					}, AkkaSystem.system2.dispatcher()).recover(new Recover<Boolean>() {
						public Boolean recover(Throwable problem) throws Throwable {
							if (problem instanceof org.neo4j.driver.v1.exceptions.TransientException
									|| problem instanceof Exception || problem != null) {
								System.out.println("RECOVER ETTİM GARİ");
								ninthAligner.increaseECByAddingPair(4, 0.0, '3');
								ninthAligner.increaseECByAddingPair(3, 0.0, '3');
								ninthAligner.increaseECByAddingPair(2, 0.0, '3');
								ninthAligner.increaseECByAddingPair(1, 50.0, '3');
								ninthAligner.increaseECByAddingPair(0, 50.0, '3');
								ninthAligner.increaseECByAddingPair(0, 0, '3');
								return true;
							}
							return false;
						}
					}, AkkaSystem.system2.dispatcher());
					
					Future<Boolean> f10 = f9.andThen(new OnComplete<Boolean>() {
						public void onComplete(Throwable failure, Boolean success) {
							if (success && failure == null) {
								System.out.println("SONRAKİ");
								tenthAligner.increaseECByAddingPair(4, 0.0, '3');
								tenthAligner.increaseECByAddingPair(3, 0.0, '3');
								tenthAligner.increaseECByAddingPair(2, 0.0, '3');
								tenthAligner.increaseECByAddingPair(1, 50.0, '3');
								tenthAligner.increaseECByAddingPair(0, 50.0, '3');
								tenthAligner.increaseECByAddingPair(0, 0, '3');
							} else if (failure != null)
								System.out.println("KAMİLLEEEER" + failure.getMessage());
						}
					}, AkkaSystem.system2.dispatcher()).recover(new Recover<Boolean>() {
						public Boolean recover(Throwable problem) throws Throwable {
							//  		if (problem instanceof Exception)
							if (problem instanceof org.neo4j.driver.v1.exceptions.TransientException
									|| problem instanceof Exception || problem != null) {
								System.out.println("RECOVER ETTİM GARİ");
								tenthAligner.increaseECByAddingPair(4, 0.0, '3');
								tenthAligner.increaseECByAddingPair(3, 0.0, '3');
								tenthAligner.increaseECByAddingPair(2, 0.0, '3');
								tenthAligner.increaseECByAddingPair(1, 50.0, '3');
								tenthAligner.increaseECByAddingPair(0, 50.0, '3');
								tenthAligner.increaseECByAddingPair(0, 0, '3');
								return true;
							}
							return false;
						}
					}, AkkaSystem.system2.dispatcher());
					
					try {
						Await.result(f2, as.timeout2.duration());
						Await.result(f4, as.timeout2.duration());
						Await.result(f6, as.timeout2.duration());
						Await.result(f8, as.timeout2.duration());
						Await.result(f10, as.timeout2.duration());

					} catch (Exception e1) {
						System.out.println("Greedy Futurelar yalan oldu::: " + e1.getMessage());
					}
				}
			} catch (ArrayIndexOutOfBoundsException aioobe) {
				// TODO Auto-generated catch block
				System.out.println("Greedy Mode or SkipChains Mode is not Activated.");
			}
			
			try {
				as.sendBestBenchmarkScoresInTime(300, 200);
				as.markBestSubGraphsInTime(300, 300);
				as.addRandomMapping(200, 200);
				as.retryPreviouslyMarkedQueries(500, 500);
			} catch (Exception e2) {
				e2.printStackTrace();
				as.sendBestBenchmarkScoresInTime(300, 200);
				as.markBestSubGraphsInTime(300, 300);
				as.addRandomMapping(200, 200);
				as.retryPreviouslyMarkedQueries(500, 500);
			}
			fourthAligner.alignClusterEdges(1, "labelpropagation",
					as.csLabelPropagationBitScore.get(0).clusterIDOfOrganism1,
					as.csLabelPropagationBitScore.get(0).clusterIDOfOrganism2, false, '3');
			fourthAligner.alignClusters(1, 0, "labelpropagation",
					as.csLabelPropagationBitScore.get(0).clusterIDOfOrganism1,
					as.csLabelPropagationBitScore.get(0).clusterIDOfOrganism2, '3');
			fifthAligner.alignClusterEdges(1, "louvain", as.csLouvainBitScore.get(0).clusterIDOfOrganism1,
					as.csLouvainBitScore.get(0).clusterIDOfOrganism2, false, '3');
			fifthAligner.alignClusters(1, 0, "louvain", as.csLouvainBitScore.get(0).clusterIDOfOrganism1,
					as.csLouvainBitScore.get(0).clusterIDOfOrganism2, '3');
		}
		
		if (cmd.hasOption("epp")) {
			for(int i =1;i<populationSize+1;i++) {
				Aligner a = new AlignerImpl(as, i);
				
				while(a.getBenchmarkScores().getSize() <=as.noofNodesInSecondNetwork-tolerance) {
				a.addMeaninglessMapping(finalMappingFactor, '3');
				a.increaseBitScoreWithTopMappings((int)(finalMappingFactor/4), '3');
				a.increaseECByAddingPair(2, 0, '3');
				a.increaseECByAddingPair(1, 0, '3');
				a.increaseECByAddingPair(0, 0, '3');
				int size1 = a.getBenchmarkScores().getSize();
				if(Math.random() < 0.3)
					a.removeBadMappingsToReduceInduction1(true, 0, 0, 0);
				int size2 = a.getBenchmarkScores().getSize();
				if(Math.random() < 0.5)
					a.removeBadMappingsRandomly(1, 1, true, 100-size2+size1);
				a.removeLatterOfManyToManyAlignments();
			} 	
			}
		}
			
			if (cmd.hasOption("sv")&&cmd.hasOption("p")&&cmd.hasOption("e")&&cmd.hasOption("l")) {
					as.writeAlignments(cmd.getOptionValue("l")+"Save", cmd.getOptionValue("p"),cmd.getOptionValue("e"));
					as.saveOldMarkedQueriesToFile(cmd.getOptionValue("l")+".txt", cmd.getOptionValue("p"));	
			} 
			
			if(cmd.hasOption("svs")&&cmd.hasOption("p")&&cmd.hasOption("e"))
				as.initializePreviousAlignmentsFromFolderAndSaveStatistics(1, cmd.getOptionValue("p"), cmd.getOptionValue("e"));
	 }

	 private void help() {
	  HelpFormatter formater = new HelpFormatter();
	  formater.setWidth(1000);
	  formater.printHelp("CLIPPI", options);
	  System.exit(0);
	 }
	 
	 public static void main(String[] args) {
		new CLIPPI(args).parse();
	}

}
