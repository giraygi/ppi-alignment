package typed.ppi;

import akka.actor.TypedActor;
import akka.dispatch.Futures;
import akka.dispatch.OnComplete;
import akka.dispatch.OnSuccess;
import akka.dispatch.Recover;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.exceptions.NoSuchRecordException;
import org.neo4j.driver.v1.summary.ResultSummary;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.helpers.TransactionTemplate;

import akka.actor.*;
import akka.japi.*;
import akka.util.Timeout;
import scala.concurrent.Await;
import scala.concurrent.ExecutionContext;
import scala.concurrent.ExecutionContextExecutor;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import akka.routing.*;

public class AkkaSystem {
	
	/**
	 * @param args
	 */
	
	static GraphDatabaseService graphDb;
	static String databaseAddress;
	FileReader fr;
	BufferedReader br;
	FileWriter fw;
	BufferedWriter bw;
	static Driver driver;
	Session session;
	int sizeOfFirstNetwork = 0;
	int sizeOfSecondNetwork = 0;
	int maxCommonAnnotations = 0;
	double maxSimilarity = 0.0;
	double averageCommonAnnotations = 0;
	double averageSimilarity = 0.0;
	int minCommonAnnotations = 0;
	double minSimilarity = 0.0;
	int numberOfSimilarityLinks = 0;
	int numberOfNodePairsWithBothSimilarityAndCommonAnnotations = 0;
	int noofAligners;
	int toleranceLimitForImprovement = 100;
	int toleranceCycleForImprovement = 25;
	static akka.actor.ActorSystem system2 = akka.actor.ActorSystem.create();
	TypedActorExtension typed = TypedActor.get(system2);
//	ExecutionContext ec = system2.dispatchers().lookup(Dispatchers.DefaultDispatcherId());
	static ExecutionContext ec = system2.dispatcher();
	static ActorRef router ;
	static double bestEC;
	static int alignerWithBestEC = 1;
	static double bestICS;
	static int alignerWithBestICS = 1;
	static double bestS3;
	static int alignerWithBestS3 = 1;
	static double bestGOC;
	static int alignerWithBestGOC = 1;
	static double bestBitscore;
	static int alignerWithBestBitscore = 1;
	static int bestSize;
	static int alignerWithBestSize = 1;
	ArrayList<ClusterSimilarity> csLouvainGO;
	ArrayList<ClusterSimilarity> csLouvainBitScore;
	ArrayList<ClusterSimilarity> csLabelPropagationGO;
	ArrayList<ClusterSimilarity> csLabelPropagationBitScore;
	List<Aligner> routees = new ArrayList<Aligner>();
	List<String> routeePaths = new ArrayList<String>();
	Aligner typedRouter = typed.typedActorOf(new TypedProps<AlignerImpl>(AlignerImpl.class), router);
	static Timeout timeout = new Timeout(Duration.create(360, "seconds"));
	Timeout timeout2 = new Timeout(Duration.create(360, "seconds"));
	static int marked = 0; 
	MetaData md;
	
	
	
	public AkkaSystem(int noofAligners, String args, int toleranceLimitForUnimprovedAligners,int toleranceCycleForUnimprovedAligners){
		this.noofAligners = noofAligners;
		this.init(args);
		this.toleranceLimitForImprovement = toleranceLimitForUnimprovedAligners;
		this.toleranceCycleForImprovement = toleranceCycleForUnimprovedAligners;
		md  = new MetaData(2);
	}
	
	public void init(String args){
		
//		GraphDatabaseSettings.BoltConnector bolt = GraphDatabaseSettings.boltConnector( "0" );

//		@SuppressWarnings("unused")
		GraphDatabaseService graphDb = new GraphDatabaseFactory()
//		        .newEmbeddedDatabase( new File("C:\\Users\\giray.tuncay\\neo4j-community-3.4.9-windows\\neo4j-community-3.4.9") );
                .newEmbeddedDatabase( new File("~/"+args) );
//		        .setConfig( bolt.enabled, "true" )
//		        .setConfig( bolt.address, "localhost:7687" )
//		        .newGraphDatabase();
		AkkaSystem.graphDb = graphDb;
		driver = GraphDatabase.driver( "bolt://localhost", AuthTokens.basic( "neo4j", "evet" ) );
		session = driver.session();
		//graphDb = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder( new File("/home/giray/Downloads/neo4j-community-3.0.6") ).newGraphDatabase();	
	}
	
	public void createGraph(String proteinsOfOrganism1, String proteinsOfOrganism2, String similaritiesOfProteins, String interactionsOfOrganism1, String interactionsOfOrganism2, String annotationsOfOrganism1, String annotationsOfOrganism2){
		
		try ( org.neo4j.driver.v1.Transaction setConstraints = session.beginTransaction() ){
			setConstraints.run("CREATE CONSTRAINT ON (n:Organism1) ASSERT n.proteinName IS UNIQUE");
			setConstraints.run("CREATE CONSTRAINT ON (n:Organism2) ASSERT n.proteinName IS UNIQUE");
//			setConstraints.run("CREATE CONSTRAINT ON ()-[r:ALIGNS]->() ASSERT exists(r.alignmentIndex)");
			setConstraints.success();
		} catch (Exception e){
			e.printStackTrace();
		}
		
		String line;
		String[] proteinPair;
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			 
			fr = new FileReader(proteinsOfOrganism1); 
			br = new BufferedReader(fr); 
			line = null;
			while((line = br.readLine())!=null)
			{ 
				tx.run("CREATE (a:Organism1 {proteinName:'"+line+"', annotations:[], power2: 0, power3: 0, power4: 0, marked:[], markedQuery:[]})");
			}
			fr = new FileReader(proteinsOfOrganism2); 
			br =  new BufferedReader(fr); 
			line = null;
			System.out.println("Nodes of the first organism are created.");
			while((line = br.readLine())!=null)
			{ 
				tx.run("CREATE (a:Organism2 {proteinName:'"+line+"', annotations:[], power2: 0, power3: 0, power4: 0, marked:[], markedQuery:[]})");
			}
			fr = new FileReader(similaritiesOfProteins);
			br =  new BufferedReader(fr); 
			line = null;
			proteinPair = null;
			System.out.println("Nodes of the second organism are created.");
			while((line = br.readLine())!=null)
			{ 
				proteinPair = line.split(" ");
				//tx.run("CREATE (n:Organism1 {proteinName: '"+proteinPair[0]+"'})-[r:SIMILARITY {similarity: "+Double.parseDouble(proteinPair[2])+"}]->(m:Organism2 {proteinName: '"+proteinPair[1]+"'}) return (r)");
				//tx.run("CREATE (n)-[r:SIMILARITY {similarity: "+Double.parseDouble(proteinPair[2])+"}]->(m) with (n),(m),(r) where n.proteinName='"+proteinPair[0]+"' and m.proteinName='"+proteinPair[1]+"' return (r)");
				tx.run("match (n:Organism1 {proteinName: '"+proteinPair[0]+"'}), (m:Organism2 {proteinName: '"+proteinPair[1]+"'}) create (n)-[:SIMILARITY {similarity: "+Double.parseDouble(proteinPair[2])+", markedQuery:[]}]->(m) ");

			}
			System.out.println("Similarities are created.");		
			
			tx.success(); tx.close();
		} catch (Exception e) {
			System.out.println("Could not create nodes and similarities");
		} 
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			 
			fr = new FileReader(interactionsOfOrganism1);
			br =  new BufferedReader(fr); 
			line = null;
			proteinPair = null;
			while((line = br.readLine())!=null)
			{
				proteinPair = line.split("\t");
				//tx.run("CREATE (n:Organism1 {proteinName: '"+proteinPair[0]+"'})-[r:INTERACTS_1]->(m:Organism1 {proteinName: '"+proteinPair[1]+"'}) return (r)");
				//tx.run("CREATE (n)-[r:INTERACTS_1]->(m) with (n),(m),(r) where n.proteinName='"+proteinPair[0]+"' and m.proteinName='"+proteinPair[1]+"' return (r)");
				tx.run("match (n:Organism1 {proteinName: '"+proteinPair[0]+"'}), (m:Organism1 {proteinName: '"+proteinPair[1]+"'}) create (n)-[:INTERACTS_1 {marked:[], markedQuery:[]}]->(m) ");
			}
			System.out.println("Interactions of the first organism are created.");
			
			tx.success(); tx.close();
		} catch (Exception e) {
			System.out.println("could not create interactions of the first organism");
		} 
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			 
			fr = new FileReader(interactionsOfOrganism2);
			br =  new BufferedReader(fr); 
			line = null;
			proteinPair = null;
			
			while((line = br.readLine())!=null)
			{
				proteinPair = line.split("\t");
				//tx.run("CREATE (n:Organism2 {proteinName: '"+proteinPair[0]+"'})-[r:INTERACTS_2]->(m:Organism2 {proteinName: '"+proteinPair[1]+"'}) return (r)");
				//tx.run("CREATE (n)-[r:INTERACTS_2]->(m) with (n),(m),(r) where n.proteinName='"+proteinPair[0]+"' and m.proteinName='"+proteinPair[1]+"' return (r)");
				tx.run("match (n:Organism2 {proteinName: '"+proteinPair[0]+"'}), (m:Organism2 {proteinName: '"+proteinPair[1]+"'}) create (n)-[:INTERACTS_2 {marked:[], markedQuery:[]}]->(m) ");
			}
			System.out.println("Interactions of the second organism are created.");
			
			tx.success(); tx.close();
		} catch (Exception e) {
			System.out.println("could not create interactions of the second organism");
		} 
		String[] proteinWithAnnotations;
		proteinWithAnnotations = null;
		StringBuilder proteinsAnnotations = new StringBuilder("['"); 
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			fr = new FileReader(annotationsOfOrganism1);
			br =  new BufferedReader(fr); 
			line = null;
			
			
			while((line = br.readLine())!=null)
			{
				proteinWithAnnotations = line.split(" ");
				proteinsAnnotations.setLength(0);
				proteinsAnnotations.append("['");
				proteinsAnnotations = new StringBuilder("['"); 
				for (int i =1; i<proteinWithAnnotations.length;i++)
					proteinsAnnotations.append(proteinWithAnnotations[i]).append("', '");
//				if(proteinsAnnotations.length()>=2)
				proteinsAnnotations.delete(proteinsAnnotations.length()-3, proteinsAnnotations.length()).append("]");
				System.out.println(proteinsAnnotations);
				tx.run("MATCH (n:Organism1 {proteinName: '"+proteinWithAnnotations[0]+"'}) SET n.annotations = "+proteinsAnnotations+" return (n)");
								
			}		
			System.out.println("Annotations of the first organism are created.");
			tx.success(); tx.close();
		} catch (Exception e) {
			System.out.println("could not create annotations of the first organism");
		} 
		
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{		
			fr = new FileReader(annotationsOfOrganism2);
			br =  new BufferedReader(fr); 
			line = null;
			proteinWithAnnotations = null;
//			proteinsAnnotations.delete(0, proteinsAnnotations.length()).append("['");
			proteinsAnnotations.setLength(0);
			proteinsAnnotations.append("['");
			while((line = br.readLine())!=null)
			{
				proteinWithAnnotations = line.split(" ");
				proteinsAnnotations.setLength(0);
				proteinsAnnotations.append("['");
			for (int i =1; i<proteinWithAnnotations.length;i++)
			proteinsAnnotations.append(proteinWithAnnotations[i]).append("', '");
//			if(proteinsAnnotations.length()>=2)
			proteinsAnnotations.delete(proteinsAnnotations.length()-3, proteinsAnnotations.length()).append("]");
			System.out.println(proteinsAnnotations);
			tx.run("MATCH (n:Organism2 {proteinName: '"+proteinWithAnnotations[0]+"'}) SET n.annotations = "+proteinsAnnotations+" return (n)");
			}
			System.out.println("Annotations of the second organism are created.");
		    tx.success(); tx.close();
		} catch(Exception e){
			System.out.println("Could not create annotations of the second organism");
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
		
	}
	
	public void computeMetaData() {
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			StatementResult result;
			result = tx.run( "match (p:Organism1)-[t:SIMILARITY]->(n:Organism2) return max(length(FILTER(x in p.annotations WHERE x in n.annotations)))");
			maxCommonAnnotations = Integer.parseInt(result.single().get("max(length(FILTER(x in p.annotations WHERE x in n.annotations)))").toString());
			System.out.println("Maximum Number of Common Annotations: "+maxCommonAnnotations);
			result = tx.run( "match (p:Organism1)-[t:SIMILARITY]->(n:Organism2) return max(t.similarity)");
			maxSimilarity = Double.parseDouble(result.single().get("max(t.similarity)").toString());
			System.out.println("Maximum Similarity: "+maxSimilarity);
			
			result = tx.run( "match (p:Organism1)-[t:SIMILARITY]->(n:Organism2) return avg(length(FILTER(x in p.annotations WHERE x in n.annotations)))");
			averageCommonAnnotations = Double.parseDouble(result.single().get("avg(length(FILTER(x in p.annotations WHERE x in n.annotations)))").toString());
			System.out.println("Average Number of Common Annotations: "+averageCommonAnnotations);
			result = tx.run( "match (p:Organism1)-[t:SIMILARITY]->(n:Organism2) return avg(t.similarity)");
			averageSimilarity = Double.parseDouble(result.single().get("avg(t.similarity)").toString());
			System.out.println("Average Similarity: "+averageSimilarity);
			
			result = tx.run( "match (p:Organism1)-[t:SIMILARITY]->(n:Organism2) return min(length(FILTER(x in p.annotations WHERE x in n.annotations)))");
			minCommonAnnotations = Integer.parseInt(result.single().get("min(length(FILTER(x in p.annotations WHERE x in n.annotations)))").toString());
			System.out.println("Minimum Number of Common Annotations: "+minCommonAnnotations);
			result = tx.run( "match (p:Organism1)-[t:SIMILARITY]->(n:Organism2) return min(t.similarity)");
			minSimilarity = Double.parseDouble(result.single().get("min(t.similarity)").toString());
			System.out.println("Minimum Similarity: "+minSimilarity);
			
			result = tx.run( "match (p:Organism1)-[t:SIMILARITY]->(n:Organism2) return count(t.similarity)");
			numberOfSimilarityLinks = Integer.parseInt(result.single().get("count(t.similarity)").toString());
			System.out.println("Number of Similarity Links: "+numberOfSimilarityLinks);
			
			result = tx.run( "match (p:Organism1)-[t:SIMILARITY]->(n:Organism2) where length(FILTER(x in p.annotations WHERE x in n.annotations)) >=1 return count(t.similarity)");
			numberOfNodePairsWithBothSimilarityAndCommonAnnotations = Integer.parseInt(result.single().get("count(t.similarity)").toString());
			System.out.println("Number of Node Pairs With Both Similarity and Common Annotations: "+numberOfNodePairsWithBothSimilarityAndCommonAnnotations);
			
			tx.success(); tx.close();
		} catch (Exception e) {
			System.err.println("Could not create metadata::: "+e.getMessage());
		}
		this.sizeOfFirstNetwork = this.countAllEdgesOfANetwork(true);
		System.out.println("Size of First Network: "+this.sizeOfFirstNetwork);
		this.sizeOfSecondNetwork = this.countAllEdgesOfANetwork(false);
		System.out.println("Size of Second Network: "+this.sizeOfSecondNetwork);
	}
	
	public void computeFunctionalMetaData() {
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			StatementResult result;	
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {	
				result = tx.run( "match (p:Organism1)-[t:SIMILARITY]->(n:Organism2) return percentileCont(length(FILTER(x in p.annotations WHERE x in n.annotations)),"+(i+1)/md.noofPercentileSteps+")");
				md.annotatedSimilarity[i] = Double.parseDouble(result.single().get("percentileCont(length(FILTER(x in p.annotations WHERE x in n.annotations)),"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Annotated Similarity "+md.annotatedSimilarity[i] );
			}
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {	
				result = tx.run( "match (p:Organism1)-[t:SIMILARITY]->(n:Organism2) return percentileCont(t.similarity,"+(i+1)/md.noofPercentileSteps+")");
				md.similarity[i] = Double.parseDouble(result.single().get("percentileCont(t.similarity,"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Similarity "+md.similarity[i] );
			}
			
			tx.success(); tx.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
				
	}
	
	public void computePowerMetaData() {
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			StatementResult result;
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (p:Organism1) return percentileCont(p.power2,"+(i+1)/md.noofPercentileSteps+")");
				md.organism1Power2[i] = Double.parseDouble(result.single().get("percentileCont(p.power2,"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Organism1Power2: "+md.organism1Power2[i] );
			}
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (n:Organism2) return percentileCont(n.power2,"+(i+1)/md.noofPercentileSteps+")");
				md.organism2Power2[i] = Double.parseDouble(result.single().get("percentileCont(n.power2,"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Organism2Power2: "+md.organism2Power2[i] );
			}
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (p:Organism1) return percentileCont(p.power3,"+(i+1)/md.noofPercentileSteps+")");
				md.organism1Power3[i] = Double.parseDouble(result.single().get("percentileCont(p.power3,"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Organism1Power3: "+md.organism1Power3[i] );
			}
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (n:Organism2) return percentileCont(n.power3,"+(i+1)/md.noofPercentileSteps+")");
				md.organism2Power3[i] = Double.parseDouble(result.single().get("percentileCont(n.power3,"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Organism2Power3: "+md.organism2Power3[i] );
			}
			
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (p:Organism1) return percentileCont(p.power4,"+(i+1)/md.noofPercentileSteps+")");
				md.organism1Power4[i] = Double.parseDouble(result.single().get("percentileCont(p.power4,"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Organism1Power4: "+md.organism1Power4[i] );
			}
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (n:Organism2) return percentileCont(n.power4,"+(i+1)/md.noofPercentileSteps+")");
				md.organism2Power4[i] = Double.parseDouble(result.single().get("percentileCont(n.power4,"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Organism2Power4: "+md.organism2Power4[i] );
			}
			
			tx.success(); tx.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void computeClusterMetaData() {
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			StatementResult result;
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (p:Organism1) return percentileCont(p.pagerank,"+(i+1)/md.noofPercentileSteps+")");
				md.organism1Pagerank[i] = Double.parseDouble(result.single().get("percentileCont(p.pagerank,"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Organism1Pagerank: "+md.organism1Pagerank[i] );
			}
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (n:Organism2) return percentileCont(n.pagerank,"+(i+1)/md.noofPercentileSteps+")");
				md.organism2Pagerank[i] = Double.parseDouble(result.single().get("percentileCont(n.pagerank,"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Organism2Pagerank: "+md.organism2Pagerank[i] );
			}
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (p:Organism1) return percentileCont(p.betweenness,"+(i+1)/md.noofPercentileSteps+")");
				md.organism1Betweenness[i] = Double.parseDouble(result.single().get("percentileCont(p.betweenness,"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Organism1Betweenness: "+md.organism1Betweenness[i] );
			}
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (n:Organism2) return percentileCont(n.betweenness,"+(i+1)/md.noofPercentileSteps+")");
				md.organism2Betweenness[i] = Double.parseDouble(result.single().get("percentileCont(n.betweenness,"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Organism2Betweenness: "+md.organism2Betweenness[i] );
			}
			
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (p:Organism1) return percentileCont(p.closeness,"+(i+1)/md.noofPercentileSteps+")");
				md.organism1Closeness[i] = Double.parseDouble(result.single().get("percentileCont(p.closeness,"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Organism1Closeness: "+md.organism1Closeness[i] );
			}
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (n:Organism2) return percentileCont(n.closeness,"+(i+1)/md.noofPercentileSteps+")");
				md.organism2Closeness[i] = Double.parseDouble(result.single().get("percentileCont(n.closeness,"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Organism2Closeness: "+md.organism2Closeness[i] );
			}
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (p:Organism1) return percentileCont(p.harmonic,"+(i+1)/md.noofPercentileSteps+")");
				md.organism1Harmonic[i] = Double.parseDouble(result.single().get("percentileCont(p.harmonic,"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Organism1Harmonic: "+md.organism1Harmonic[i] );
			}
			
			for (int i = 0;i<md.noofPercentileSteps;i++) {
				result = tx.run( "match (n:Organism2) return percentileCont(n.harmonic,"+(i+1)/md.noofPercentileSteps+")");
				md.organism2Harmonic[i] = Double.parseDouble(result.single().get("percentileCont(n.harmonic,"+(i+1)/md.noofPercentileSteps+")").toString());
				System.out.println(i+". Organism2Harmonic: "+md.organism2Harmonic[i] );
			}
			
			tx.success(); tx.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	public int computeOrderOfClusterForGivenAverageFactor(ArrayList<ClusterSimilarity> cs, double averageFactor){
		double sum = 0.0;
		for (int i = 0;i<cs.size();i++) {
				sum+=cs.get(i).similarity;
		}
		
		double average = sum/cs.size();
		double aggregateAverage = average*averageFactor;
		
		for (int i = 0;i<cs.size();i++) {
		if	((cs.get(i).similarity)<aggregateAverage) {
			return i;
		}
			
	}
		
		return 0;
	}
	
	
	public void computePowers(){
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			tx.run("match (n:Organism1)-[i:INTERACTS_1]-(m:Organism1) with (n),count(n) as connections set n.power2 = connections");
			tx.run("match (n:Organism1)-[i:INTERACTS_1]-(m:Organism1)-[i2:INTERACTS_1]-(o:Organism1)-[i3:INTERACTS_1]-(n) with (n),count(n) as connections set n.power3 = connections");
			tx.run("match (a:Organism1)-[r1:INTERACTS_1]-(b:Organism1)-[r2:INTERACTS_1]-(c:Organism1)-[r3:INTERACTS_1]-(a:Organism1),(c:Organism1)-[r4:INTERACTS_1]-(d:Organism1)-[r5:INTERACTS_1]-(a:Organism1),(d:Organism1)-[r6:INTERACTS_1]-(b:Organism1) with (a),count(a) as connections set a.power4 = connections");
			tx.run("match (n:Organism2)-[i:INTERACTS_2]-(m:Organism2) with (n),count(n) as connections set n.power2 = connections");
			tx.run("match (n:Organism2)-[i:INTERACTS_2]-(m:Organism2)-[i2:INTERACTS_2]-(o:Organism2)-[i3:INTERACTS_2]-(n) with (n),count(n) as connections set n.power3 = connections");
			tx.run("match (e:Organism2)-[r7:INTERACTS_2]-(f:Organism2)-[r8:INTERACTS_2]-(g:Organism2)-[r9:INTERACTS_2]-(e:Organism2),(g:Organism2)-[r10:INTERACTS_2]-(h:Organism2)-[r11:INTERACTS_2]-(e:Organism2),(h:Organism2)-[r12:INTERACTS_2]-(f:Organism2) with (e),count(e) as connections set e.power4 = connections");
			System.out.println("Powers are computed for all Nodes");
		      
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	public void computePageRank(int iterations, double dampingFactor) {
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			tx.run("CALL algo.pageRank('Organism2', 'INTERACTS_2',\n" + 
					"  {iterations:"+iterations+", dampingFactor:"+dampingFactor+", write: true,writeProperty:'pagerank'})\n" + 
					"YIELD nodes, iterations, loadMillis, computeMillis, writeMillis, dampingFactor, write, writeProperty");
			
			tx.run("CALL algo.pageRank('Organism1', 'INTERACTS_1',\n" + 
					"  {iterations:"+iterations+", dampingFactor:"+dampingFactor+", write: true,writeProperty:'pagerank'})\n" + 
					"YIELD nodes, iterations, loadMillis, computeMillis, writeMillis, dampingFactor, write, writeProperty");
		     System.out.println("Page Rank Values are computed for all Nodes"); 
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	// Proteinler arası İlişkilerin yönleri bir şey ifade etmediği için direction özelliği "both" olarak seçilmiştir. Yön bilgisi anlamlı olsaydı "in" ya da "out" olarak da seçilebilirdi. 
	public void computeBetweennessCentrality() {
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			tx.run("CALL algo.betweenness('Organism2','INTERACTS_2', {direction:'both',write:true, writeProperty:'betweenness'})\n" + 
					"YIELD nodes, minCentrality, maxCentrality, sumCentrality, loadMillis, computeMillis, writeMillis;");
			
			tx.run("CALL algo.betweenness('Organism1','INTERACTS_1', {direction:'both',write:true, writeProperty:'betweenness'})\n" + 
					"YIELD nodes, minCentrality, maxCentrality, sumCentrality, loadMillis, computeMillis, writeMillis;");
			System.out.println("Betweenness Centrality Values are computed for all Nodes");  
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	// Çalışmıyordu etiket parametresi yerine desen verince düzeldi
	public void computeClosenessCentrality() {
			
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			tx.run("CALL algo.closeness(\n" + 
					"  'MATCH (p:Organism2) RETURN id(p) as id',\n" + 
					"  'MATCH (p1:Organism2)-[:INTERACTS_2]-(p2:Organism2) RETURN id(p1) as source, id(p2) as target',\n" + 
					"  {graph:'cypher', write: true, writeProperty:'closeness'}\n" + 
					");");
			
			tx.run("CALL algo.closeness(\n" + 
					"  'MATCH (p:Organism1) RETURN id(p) as id',\n" + 
					"  'MATCH (p1:Organism1)-[:INTERACTS_1]-(p2:Organism1) RETURN id(p1) as source, id(p2) as target',\n" + 
					"  {graph:'cypher', write: true, writeProperty:'closeness'}\n" + 
					");");
			System.out.println("Closeness Centrality Values are computed for all Nodes");   
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}

	public void computeHarmonicCentrality() {
		
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{
		tx.run("CALL algo.closeness.harmonic('Organism2', 'INTERACTS_2', {write:true, writeProperty:'harmonic'})\n" + 
				"	YIELD nodes,loadMillis, computeMillis, writeMillis;");
		
		tx.run("CALL algo.closeness.harmonic('Organism1', 'INTERACTS_1', {write:true, writeProperty:'harmonic'})\n" + 
				"	YIELD nodes,loadMillis, computeMillis, writeMillis;");
		System.out.println("Harmonic Centrality Values are computed");  
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
}
	
public void computeLouvainCommunities() {
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{
		tx.run("CALL algo.louvain(\n" + 
				"			  'MATCH (p:Organism1) RETURN id(p) as id',\n" + 
				"			  'MATCH (p1:Organism1)-[f:INTERACTS_1]-(p2:Organism1)\n" + 
				"			   RETURN id(p1) as source, id(p2) as target, f.weight as weight',\n" + 
				"			  {graph:'cypher',write:true,writeProperty:'louvain'})");
		
		tx.run("CALL algo.louvain(\n" + 
				"			  'MATCH (p:Organism2) RETURN id(p) as id',\n" + 
				"			  'MATCH (p1:Organism2)-[f:INTERACTS_2]-(p2:Organism2)\n" + 
				"			   RETURN id(p1) as source, id(p2) as target, f.weight as weight',\n" + 
				"			  {graph:'cypher',write:true,writeProperty:'louvain'})");
		System.out.println("Louvain Communities are computed");  
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
}

public void computeLabelPropagationCommunities(int iterations) {
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{
		tx.run("CALL algo.labelPropagation('Organism1', 'INTERACTS_1','BOTH',\n" + 
				"			  {iterations:"+iterations+",partitionProperty:'labelpropagation', write:true})\n" + 
				"			YIELD nodes, iterations, loadMillis, computeMillis, writeMillis, write, partitionProperty;");
		
		tx.run("CALL algo.labelPropagation('Organism2', 'INTERACTS_2','BOTH',\n" + 
				"			  {iterations:"+iterations+",partitionProperty:'labelpropagation', write:true})\n" + 
				"			YIELD nodes, iterations, loadMillis, computeMillis, writeMillis, write, partitionProperty;");
		System.out.println("Label Propagation Communities are computed");  
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	
}

public void computeSCCCommunities() {
//	String tx = "CALL algo.scc('MATCH (p:Organism2) RETURN id(p) as id','MATCH (p1:Organism2)-[f:INTERACTS_2]-(p2:Organism2)-[f2:INTERACTS_2]-(p3:Organism2)-[f3:INTERACTS_2]-(p1) RETURN id(p1) as source, id(p2) as target, f.weight as weight', {write:true,partitionProperty:'scc', graph: 'cypher'})\n" + 
//	"YIELD loadMillis, computeMillis, writeMillis, setCount, maxSetSize, minSetSize;";
	
//	String txuni = "CALL algo.unionFind('MATCH (p:Organism2) RETURN id(p) as id','MATCH (p1:Organism2)-[f:INTERACTS_2]-(p2:Organism2)-[f2:INTERACTS_2]-(p3:Organism2)-[f3:INTERACTS_2]-(p1) RETURN id(p1) as source, id(p2) as target, f.weight as weight', {write:true,partitionProperty:'union', graph: 'cypher'}) YIELD loadMillis, computeMillis, writeMillis, setCount";
}

//clusterNo her bir organizmada hesaplanacak en büyük küme sayısıdır.

public void computeClusterSimilarities(final String clusterType,int clusterNo) {
	
	double[] bitscoreSums = new double[(clusterNo*clusterNo)];
	double[] commonGOSums = new double[(clusterNo*clusterNo)];;
	int[] o1Clusters = new int[clusterNo];
	int[] o2Clusters = new int[clusterNo];
	int[] o1ClusterSizes = new int[clusterNo];
	int[] o2ClusterSizes = new int[clusterNo];
	StatementResult result = null;
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction();
			BufferedWriter bw = new BufferedWriter(new FileWriter(clusterType+"1")))
	{
		result = tx.run("match (n:Organism1) return distinct(n."+clusterType+") as clustertype,count(n) order by count(n) desc limit "+clusterNo+"");
		int count = 0;
		while(result.hasNext()){
			Record row = result.next();
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null)		
					if (column.getKey().equals("clustertype"))
						o1Clusters[count] = row.get( column.getKey() ).asInt();
					else if (column.getKey().equals("count(n)"))
						o1ClusterSizes[count] = row.get( column.getKey() ).asInt();
			}
			bw.write(o1Clusters[count]+" "+o1ClusterSizes[count]);
			bw.newLine();
			count++;
		}
		tx.success(); tx.close();
		bw.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction();
			BufferedWriter bw = new BufferedWriter(new FileWriter(clusterType+"2")) )
	{
		result = tx.run("match (n:Organism2) return distinct(n."+clusterType+") as clustertype,count(n) order by count(n) desc limit "+clusterNo+"");
		int count = 0;
		while(result.hasNext()){
			Record row = result.next();
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null)		
					if (column.getKey().equals("clustertype"))
						o2Clusters[count] = row.get( column.getKey() ).asInt();
					else if (column.getKey().equals("count(n)"))
						o2ClusterSizes[count] = row.get( column.getKey() ).asInt();
			}
			bw.write(o2Clusters[count]+" "+o2ClusterSizes[count]);
			bw.newLine();
			count++;
		}
		tx.success(); tx.close();
		bw.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	
	//devamı uzun. Her kümenin biribiri ile toplam benzerlikleri bulup eleman sayılarına bölmek gerekiyor.
	//alttakini döngüye sokcaz.
	try (BufferedWriter bw = new BufferedWriter(new FileWriter(clusterType+"BitScore"))){
		for (int i = 0;i<clusterNo;i++)
			for (int j = 0;j<clusterNo;j++)
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			result = tx.run("match (n:Organism2) <-[s:SIMILARITY]-(m:Organism1) where n."+clusterType+" = "+o2Clusters[j]+" and m."+clusterType+" = "+o1Clusters[i]+" return sum(s.similarity)");
				bitscoreSums[i*clusterNo+j] = Double.parseDouble(result.single().get("sum(s.similarity)").toString())/(o1ClusterSizes[i]+o2ClusterSizes[j]);
			bw.write(o1Clusters[i]+" "+o2Clusters[j]+" "+bitscoreSums[i*clusterNo+j] );
			bw.newLine();
			tx.success(); tx.close();
			
		} catch (Exception e){
			bitscoreSums[i*clusterNo+j] = 0;
			System.out.println(e.getMessage());
		}
		bw.close();
	} catch (Exception e1) {
		// TODO Auto-generated catch block
		e1.printStackTrace();
	}
	try (BufferedWriter bw = new BufferedWriter(new FileWriter(clusterType+"commonGO")) ){
		for (int i = 0;i<clusterNo;i++)
			for (int j = 0;j<clusterNo;j++)
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			// Birer birer toplamak yerine sum(length(FILTER(x in n.annotations WHERE x in m.annotations))) kullanılabilir.
			result = tx.run("match (n:Organism2),(m:Organism1) where n."+clusterType+" = "+o2Clusters[j]+" and m."+clusterType+" = "+o1Clusters[i]+" return sum(length(FILTER(x in n.annotations WHERE x in m.annotations)))");
			
			commonGOSums[i*clusterNo+j]  = Double.parseDouble(result.single().get("sum(length(FILTER(x in n.annotations WHERE x in m.annotations)))").toString())/(o1ClusterSizes[i]+o2ClusterSizes[j]);
			bw.write(o1Clusters[i]+" "+o2Clusters[j]+" "+commonGOSums[i*clusterNo+j] );
			bw.newLine();
			tx.success(); tx.close();
		} catch (Exception e){
			commonGOSums[i*clusterNo+j] = 0;
			System.out.println(e.getMessage());
		}
		bw.close();
	} catch (Exception e) {
		e.printStackTrace();
	}	
	
}
// benchmark options: commonGO,  BitScore
public ArrayList<ClusterSimilarity> sortSimilarClusters(String clusterType, String benchmark) {
	
	ArrayList<ClusterSimilarity> alcs = new ArrayList<ClusterSimilarity>();
	
	try {
		fr = new FileReader(clusterType+benchmark);
	} catch (FileNotFoundException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	br =  new BufferedReader(fr); 
	String line = null;
	String[] clusterPairWithSimilarity;
	try {
		while((line = br.readLine())!=null)
		{
			clusterPairWithSimilarity = line.split(" ");
			alcs.add(new ClusterSimilarity(Long.parseLong(clusterPairWithSimilarity[0]),Long.parseLong(clusterPairWithSimilarity[1]),Double.parseDouble((clusterPairWithSimilarity[2]))));
		}
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	Collections.sort(alcs, new Comparator<ClusterSimilarity>(){

		@Override
		public int compare(ClusterSimilarity arg0, ClusterSimilarity arg1) {
			// TODO Auto-generated method stub
			if (arg0.similarity<arg1.similarity)
			return 1;
			else if (arg0.similarity==arg1.similarity)
				return 0;
			else return -1;
//			return (int) Math.ceil(arg1.similarity - arg0.similarity);
		}
		
	});
	System.out.println("Printing similarities of "+clusterType+" : "+benchmark);
	for(ClusterSimilarity cs: alcs){
		System.out.println(cs.similarity);
   }
	
	return alcs;
}
		
	public void deleteAllNodesRelationships(){
		System.out.println("DELETING ALL");
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
		      tx.run( "MATCH (n) DETACH DELETE n" );
		      System.out.println("Previous data cleared");
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
	}
	
	public void removeAllAlignments(){
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
	    {
			tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1) delete r");
			System.out.println("Previous alignments are deleted.");
			tx.success(); tx.close();
	    } catch (Exception e){
	    	  e.printStackTrace();
	      }

	}
	
public void removeIdenticalAlignments(boolean removeOlder){
	System.out.println("removeIdenticalAlignments");
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
    {
		if(removeOlder)
		tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1)<-[k:ALIGNS]-(n:Organism2) where r.alignmentNumber = k.alignmentNumber and r.alignmentIndex = k.alignmentIndex and id(r) < id(k) delete r");
		else
		tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1)<-[k:ALIGNS]-(n:Organism2) where r.alignmentNumber = k.alignmentNumber and r.alignmentIndex = k.alignmentIndex and id(r) >= id(k) delete r");
		tx.success(); tx.close();
    } catch (Exception e){
    	  e.printStackTrace();
      }

}

public void removeLatterOfManyToManyAlignments(){
	System.out.println("removeLatterOfManyToManyAlignments");
	
	TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
	tm.failure(new Throwable("Herkesin tuttuğu kendine"));
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
	int removed = template.with(AkkaSystem.graphDb).execute( transaction -> {
		Session rwlma = AkkaSystem.driver.session();
		ResultSummary rs = null;
		int count = 0;
	try ( org.neo4j.driver.v1.Transaction tx = rwlma.beginTransaction())
    {
		rs = tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1), (o:Organism2)-[q:ALIGNS]->(p:Organism1) where startNode(r) = startNode(q) and r.alignmentIndex CONTAINS  q.alignmentIndex and id(r) >= id(q) delete r").consume();
		count+=rs.counters().relationshipsDeleted();
		rs = tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1), (o:Organism2)-[q:ALIGNS]->(p:Organism1) where endNode(r) = endNode(q) and r.alignmentIndex CONTAINS q.alignmentIndex and id(r) >= id(q) delete r").consume();
		count+=rs.counters().relationshipsDeleted();
		tx.success(); tx.close();
    } catch (Exception e){
    	System.err.println("removeLatterOfManyToManyAlignments::: "+e.getMessage());
    	if(Math.random() < 0.5)
    		removeLatterOfManyToManyAlignments();
      } finally {rwlma.close();}
    return count;
} );
if(removed>0)
	System.err.println(removed+" Subsequent Mappings are Removed!!!");
}


// Bu metot AlignerImpl'dan buraya taşındı ve aynı anda tüm hizalayıcılar için çalışması sağlandı. 
public void removeWeakerOfManyToManyAlignments(){
	
	System.out.println("removeWeakerOfManyToManyAlignments");
	
	TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
	tm.failure(new Throwable("Herkesin tuttuğu kendine"));
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
	int removed = template.with(AkkaSystem.graphDb).execute( transaction -> {
		Session rwmma = AkkaSystem.driver.session();
		StatementResult result;
		int count = 0;
		ResultSummary rs = null;
		try ( org.neo4j.driver.v1.Transaction tx = rwmma.beginTransaction())
	    {
			result = tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1), (o:Organism2)-[q:ALIGNS]->(p:Organism1) "
			+ "where r.alignmentIndex CONTAINS q.alignmentIndex and startNode(r) = startNode(q) return id(r),id(q)");
			
			long r = 0;
			long q = 0;
			long temp = 0;
			
			while(result.hasNext()){
				Record row = result.next();
				for ( Entry<String,Object> column : row.asMap().entrySet() ){
					if(column.getValue()!=null)
						switch (column.getKey()) {
						case "id(r)":
							r = row.get( column.getKey() ).asLong();
							break;
						case "id(q)":
							q = row.get( column.getKey() ).asLong();
							break;
						default:
							System.out.println("Unexpected column"+column.getKey());
							break;
						}
					}
				
				temp = compareSimilarityContribution(r, q);
				if (temp==r) {
					rs = tx.run("match ()-[q:ALIGNS]-() where id(q) ="+q+" delete q").consume();
					count+=rs.counters().relationshipsDeleted();
					rs = null;
				}
					
				else {
					rs = tx.run("match ()-[r:ALIGNS]-() where id(r) ="+r+" delete r").consume();
					count+=rs.counters().relationshipsDeleted();
					rs = null;
				}
					
				r = 0;
				q = 0;
				temp = 0;
				
				}
			
			result = tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1), (o:Organism2)-[q:ALIGNS]->(p:Organism1) where endNode(r) = endNode(q) and r.alignmentIndex = q.alignmentIndex return id(r),id(q)");
			
			r = 0;
			q = 0;
			temp = 0;
			
			while(result.hasNext()){
				Record row = result.next();
				for ( Entry<String,Object> column : row.asMap().entrySet() ){
					if(column.getValue()!=null)
						switch (column.getKey()) {
						case "id(r)":
							r = row.get( column.getKey() ).asLong();
							break;
						case "id(q)":
							q = row.get( column.getKey() ).asLong();
							break;
						default:
							System.out.println("Unexpected column"+column.getKey());
							break;
						}
					}
				
				temp = compareSimilarityContribution(r, q);
				rs = tx.run("match ()-[a:ALIGNS]-() where id(a) ="+temp+" delete a").consume();
				count+=rs.counters().relationshipsDeleted();
				rs = null;
				r = 0;
				q = 0;
				temp = 0;
				}
			
			tx.success(); tx.close();
	    } catch (Exception e){
	    	System.err.println("removeWeakerOfManyToManyAlignments::: "+e.getMessage());
	    	if(Math.random() < 0.5)
	    		removeWeakerOfManyToManyAlignments();
	      }
		
		finally {rwmma.close();}
	    return count;
	} );
		System.err.println(removed+" Weaker Mappings are Removed!!!");
}

private long compareSimilarityContribution(long relationshipID1, long relationshipID2){
	
	TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
	tm.failure(new Throwable("Herkesin tuttuğu kendine"));
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
	
	long greater = template.with(AkkaSystem.graphDb).execute( transaction -> {
		StatementResult result;
		int powersum1 = 0;
		int powersum2 = 0;
		int commonAnnotations1 = 0;
		int commonAnnotations2 = 0;
		double similarity1 = 0.0;
		double similarity2 = 0.0;
		Record r = null;
		Session csc = AkkaSystem.driver.session();
		
		try ( org.neo4j.driver.v1.Transaction tx = csc.beginTransaction())
	    {
			
			result = tx.run("match (n1:Organism2)-[r1:ALIGNS]->(p1:Organism1), (n2:Organism2)-[r2:ALIGNS]->(p2:Organism1) "
					+ "where r2.alignmentNumber =~ r1.alignmentNumber and id(r2) = "+relationshipID2+" and id(r1) = "+relationshipID1+" "
					+ "optional match (p1)-[t1:SIMILARITY]->(n1) optional match (p2)-[t2:SIMILARITY]->(n2) return length(FILTER(x in p2.annotations WHERE x in n2.annotations)) as commonAnnotations2, length(FILTER(x in p1.annotations WHERE x in n1.annotations)) as commonAnnotations1,t2.similarity,t1.similarity,"
					+ "min(p1.power2+p1.power3+p1.power4 ,n1.power2+n1.power3+n1.power4) as powersum1,min(p2.power2+p2.power3+p2.power4,n2.power2+n2.power3+n2.power4) as powersum2");
			
			r = result.single();
			
			try {
				powersum1 = Integer.parseInt(r.get("powersum1").toString());
			} catch (Exception e1) {
				System.out.println("1st Power Number Format Exception: "+e1.getMessage());
			}
			try {
				powersum2 = Integer.parseInt(r.get("powersum2").toString());
			} catch (Exception e1) {
				System.out.println("2nd Power Number Format Exception: "+e1.getMessage());
			}
			try {
				commonAnnotations1 = Integer.parseInt(r.get("commonAnnotations1").toString());
			} catch (Exception e1) {
				System.out.println("1st Annotation Number Format Exception: "+e1.getMessage());
			}
			try {
				commonAnnotations2 = Integer.parseInt(r.get("commonAnnotations2").toString());
			} catch (Exception e1) {
				System.out.println("2nd Annotation Number Format Exception: "+e1.getMessage());
			}
			try {
				similarity1 = Double.parseDouble(r.get("t1.similarity").toString());
			} catch (NumberFormatException nfe) {
			}
			try {
				similarity2 = Double.parseDouble(r.get("t2.similarity").toString());
			} catch (Exception e) {
			}		
			
			tx.success(); tx.close();
	    } 
			catch(NoSuchRecordException nsre){
			}
		
	    catch (Exception e){
	    	  System.err.println("compareSimilarityContribution::: "+e.getMessage());
	    	  compareSimilarityContribution(relationshipID1, relationshipID2);
	      } finally {
	    	  csc.close();
	      }
		
		if (commonAnnotations1*similarity1+powersum1 > commonAnnotations2*similarity2+powersum2)
			return relationshipID1;
		else if (commonAnnotations1*similarity1 + powersum1< commonAnnotations2*similarity2+powersum2)
			return relationshipID2;
		else if (powersum1>powersum2)
			return relationshipID1;
		else if (powersum1<powersum2)
			return relationshipID2;
		else if (similarity1 > similarity2)
			return relationshipID1;
		else if (similarity1 < similarity2)
			return relationshipID2;
		else if (commonAnnotations1 > commonAnnotations2)
			return relationshipID1;
		else if (commonAnnotations1 < commonAnnotations2)
			return relationshipID2;
			return (long) 0;
	} );	
	return greater;
}

// Sıralı olması gerekiyordu !!!
public ArrayList<PowerNode> findCentralNodes2(boolean organism1, int limit){
	StatementResult result;
	ArrayList<PowerNode> nodes = new ArrayList<PowerNode>();
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
    {
		if(organism1)
		result = tx.run("match (n:Organism1)-[i:INTERACTS_1]-(m:Organism1) return (n),count(m) as connections order by connections desc limit "+limit+"");
		else
		result = tx.run("match (n:Organism2)-[i:INTERACTS_2]-(m:Organism2) return (n),count(m) as connections order by connections desc limit "+limit+"");
		Record record;
		while(result.hasNext()){
			record = result.next();
			nodes.add(new PowerNode(record.get(0).asNode(),record.get(1).asInt()));
			tx.success(); tx.close();
		}
    } catch (Exception e){
    	  e.printStackTrace();
      }
	return nodes;
	
}
// match (n:Organism1)-[i:INTERACTS_1]-(m:Organism1)-[i2:INTERACTS_1]-(o:Organism1)-[i3:INTERACTS_1]-(n) with count(n) as connections set n.power = connections
public ArrayList<PowerNode> findCentralNodes3(boolean organism1, int limit){
	StatementResult result;
	ArrayList<PowerNode> nodes = new ArrayList<PowerNode>();
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
    {
		if(organism1)
		result = tx.run("match (n:Organism1)-[i:INTERACTS_1]-(m:Organism1)-[i2:INTERACTS_1]-(o:Organism1)-[i3:INTERACTS_1]-(n) return (n),count(i2) as connections order by connections desc limit "+limit+"");
		else
		result = tx.run("match (n:Organism2)-[i:INTERACTS_2]-(m:Organism2)-[i2:INTERACTS_2]-(o:Organism2)-[i3:INTERACTS_2]-(n) return (n),count(i2) as connections order by connections desc limit "+limit+"");
		Record record;
		while(result.hasNext()){
			record = result.next();
			nodes.add(new PowerNode(record.get(0).asNode(),record.get(1).asInt()));
		}
		tx.success(); tx.close();
    } catch (Exception e){
    	  e.printStackTrace();
      }
	return nodes;
	
}

public ArrayList<PowerNode> findCentralNodes4(boolean organism1, int limit){
	StatementResult result;
	ArrayList<PowerNode> nodes = new ArrayList<PowerNode>();
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
    {
		if(organism1)
		result = tx.run("match (a:Organism1)-[r1:INTERACTS_1]-(b:Organism1)-[r2:INTERACTS_1]-(c:Organism1)-[r3:INTERACTS_1]-(a:Organism1),(c:Organism1)-[r4:INTERACTS_1]-(d:Organism1)-[r5:INTERACTS_1]-(a:Organism1),(d:Organism1)-[r6:INTERACTS_1]-(b:Organism1) return a,count(a) as connections order by connections desc limit "+limit+"");
		else
		result = tx.run("match (e:Organism2)-[r7:INTERACTS_2]-(f:Organism2)-[r8:INTERACTS_2]-(g:Organism2)-[r9:INTERACTS_2]-(e:Organism2),(g:Organism2)-[r10:INTERACTS_2]-(h:Organism2)-[r11:INTERACTS_2]-(e:Organism2),(h:Organism2)-[r12:INTERACTS_2]-(f:Organism2) return e,count(e) as connections order by connections desc limit "+limit+"");
		Record record;
		while(result.hasNext()){
			record = result.next();
			nodes.add(new PowerNode(record.get(0).asNode(),record.get(1).asInt()));
		}
		tx.success(); tx.close();
    } catch (Exception e){
    	  e.printStackTrace();
      }
	return nodes;
	
}

public void markUnionOfQueries(String queryNumber1,String queryNumber2, String unionNumber){
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{
	tx.run("match (n) where not ANY(x IN n.markedQuery WHERE x = '"+unionNumber+"') and (ANY(x IN n.markedQuery WHERE x = '"+queryNumber1+"') or ANY(x IN n.markedQuery WHERE x = '"+queryNumber2+"')) set n.markedQuery = n.markedQuery + '"+unionNumber+"' return (n)");
	tx.run("match ()-[n]-() where not ANY(x IN n.markedQuery WHERE x = '"+unionNumber+"') and (ANY(x IN n.markedQuery WHERE x = '"+queryNumber1+"') or ANY(x IN n.markedQuery WHERE x = '"+queryNumber2+"')) set n.markedQuery = n.markedQuery + '"+unionNumber+"' return (n)");
	tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}

}

public void markIntersectionOfQueries(String queryNumber1,String queryNumber2, String intersectionNumber){
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{
	tx.run("match (n) where not ANY(x IN n.markedQuery WHERE x = '"+intersectionNumber+"') and (ANY(x IN n.markedQuery WHERE x = '"+queryNumber1+"') and ANY(x IN n.markedQuery WHERE x = '"+queryNumber2+"')) set n.markedQuery = n.markedQuery + '"+intersectionNumber+"' return (n)");
	tx.run("match ()-[n]-() where not ANY(x IN n.markedQuery WHERE x = '"+intersectionNumber+"') and (ANY(x IN n.markedQuery WHERE x = '"+queryNumber1+"') and ANY(x IN n.markedQuery WHERE x = '"+queryNumber2+"')) set n.markedQuery = n.markedQuery + '"+intersectionNumber+"' return (n)");
	tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
} 

public void markDifferenceOfQueries(String queryNumber1,String queryNumber2, String differenceNumber){
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{
	tx.run("match (n) where not ANY(x IN n.markedQuery WHERE x = '"+differenceNumber+"') and (ANY(x IN n.markedQuery WHERE x = '"+queryNumber1+"') and not ANY(x IN n.markedQuery WHERE x = '"+queryNumber2+"')) set n.markedQuery = n.markedQuery + '"+differenceNumber+"' return (n)");
	tx.run("match ()-[n]-() where not ANY(x IN n.markedQuery WHERE x = '"+differenceNumber+"') and (ANY(x IN n.markedQuery WHERE x = '"+queryNumber1+"') and not ANY(x IN n.markedQuery WHERE x = '"+queryNumber2+"')) set n.markedQuery = n.markedQuery + '"+differenceNumber+"' return (n)");
	tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
}

public void removeQuery(String queryNumber){
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{
	tx.run("MATCH (n) WHERE EXISTS(n.markedQuery) SET n.markedQuery = FILTER(x IN n.markedQuery WHERE x <> '"+queryNumber+"')");	
	tx.run("MATCH ()-[n]-() WHERE EXISTS(n.markedQuery) SET n.markedQuery = FILTER(x IN n.markedQuery WHERE x <> '"+queryNumber+"')");	
	tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
}

//AkkaSystemde bulunması daha doğru olabilir.
public void unmarkConservedStructureQuery(int markedQuery) {
	
	TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
	tm.failure(new Throwable("Herkesin tuttuğu kendine"));
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
	boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
		Session unMarkSession = AkkaSystem.driver.session();
		try{
			unMarkSession.run("MATCH (n) WHERE EXISTS(n.markedQuery) SET n.markedQuery = FILTER(x IN n.markedQuery WHERE x <> '"+markedQuery+"')");
			unMarkSession.run("MATCH ()-[r:ALIGNS]->() WHERE EXISTS(r.markedQuery) SET r.markedQuery = FILTER(x IN r.markedQuery WHERE x <> '"+markedQuery+"')");
		} catch(Exception e){
			System.err.println("Unmark all nodes::: "+e.getMessage());
			unmarkConservedStructureQuery(markedQuery) ;
		} finally {unMarkSession.close();}
		return true;
	} );
	if(success)
		System.out.println("Unmark Conserved Structure Query was successful.");
	else
		System.err.println("Unmark Conserved Structure Query was interrupted!");
	
}

public void removeAllQueries(){
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{
	tx.run("MATCH (n) WHERE EXISTS(n.markedQuery) SET n.markedQuery = []");	
	tx.run("MATCH ()-[n]-() WHERE EXISTS(n.markedQuery) SET n.markedQuery = []");	
	tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
}

public void removeAllMarks(){
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{
	tx.run("MATCH (n) WHERE EXISTS(n.markedQuery) SET n.marked = []");	
	tx.run("MATCH ()-[n]-() WHERE EXISTS(n.markedQuery) SET n.marked = []");	
	tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
}

//AkkaSystemde bulunması daha doğru olabilir.
public void unmarkAllConservedStructureQueries() {
	
	TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
	tm.failure(new Throwable("Herkesin tuttuğu kendine"));
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
	boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
		Session unMarkSession = AkkaSystem.driver.session();
		try{
			unMarkSession.run("MATCH (n) WHERE EXISTS(n.markedQuery) SET n.markedQuery = []");
			unMarkSession.run("MATCH ()-[r:ALIGNS]->() WHERE EXISTS(r.markedQuery) SET r.markedQuery = []");
		} catch(Exception e){
			System.err.println("Unmark all nodes::: "+e.getMessage());
			unmarkAllConservedStructureQueries() ;
		} finally {unMarkSession.close();}
		return true;
	} );
	
	if(success)
		System.out.println("Unmark All Conserved Structure Queries was successful.");
	else
		System.err.println("Unmark All Conserved Structure Queries was interrupted!");
	
}

// Yarıda kesilmiş bir uygulamaya eski işaretli sorguları tekrar veritabanından yüklemek için kullanılır.
public void loadOldMarkedQueriesFromDBToApplication() {
	StatementResult result;
	Record record;
	Set<BenchmarkScores> markedQueries = null;
	int lastValueOfReceived = 0;
		
		Session loadQuerySession = AkkaSystem.driver.session();
		try{
			
			for (Aligner aligner : routees) {
				result = loadQuerySession.run("match ()-[a:ALIGNS]-() where a.alignmentNumber = '"+aligner.getAlignmentNo()+"' return a.markedQuery");
				markedQueries = new HashSet<BenchmarkScores>();
				while(result.hasNext()){
					record = result.next();
					
					for (Object o : record.get(0).asList()) {
						if(Long.valueOf((String)o)>lastValueOfReceived)
							lastValueOfReceived = Long.valueOf((String)o).intValue();
						markedQueries.add(new BenchmarkScores(aligner.getAlignmentNo(),Long.valueOf((String)o).longValue()));
						if(Long.valueOf((String)o).longValue()>AkkaSystem.marked)
							AkkaSystem.marked = Integer.valueOf((String)o).intValue()+1;
					}
					aligner.addMarkedQueries(markedQueries);
					
				}				
			}
			System.out.println("loaded number of marked queries: "+AkkaSystem.marked);
			AlignerImpl.received = lastValueOfReceived+1;
		} catch(Exception e){
			System.err.println("Load Marked Queries::: "+e.getMessage());
			unmarkAllConservedStructureQueries() ;
		} finally {loadQuerySession.close();}
}

//Test Edilmedi. Yanlış gibi. Yarıda bırakılmış uygulamanın işaretli sorgularını dosya adına göre dosyadan veritabanına yükler.
public void loadOldMarkedQueriesFromFileToDB(String fileName) {
	
	try {
		fr = new FileReader(fileName);
	} catch (FileNotFoundException e1) {
		// TODO Auto-generated catch block
		e1.printStackTrace();
	}
	br =  new BufferedReader(fr); 
	String line = null;
	String[] markedQueries;
	Session saveOldQuerySession = AkkaSystem.driver.session();
	
	try {
		while((line = br.readLine())!=null)
		{ 
			markedQueries = line.split(" ");
			if(markedQueries.length >=2) {
				// burada bir stringbuilderla ilgili alignment birden çok değerle set işlemi yapılacak.
				StringBuilder sb = new StringBuilder(" set a.markedQuery = a.markedQuery + [");
				for (int i =1;i<markedQueries.length;i++) {
					sb.append("'"+markedQueries[i]+"'");
					if(i<=markedQueries.length-2)
						sb.append(", ");
					else
						sb.append("]");
				}
				saveOldQuerySession.run("match ()-[a:ALIGNS]->() where a.alignmentIndex = '"+markedQueries[0]+"'");	
			}
		}
	} catch (Exception e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
	
} 
// Hizalayıcı numaraları elle veriliyor. Ön tanımlı: 1-10
//Uygulamada mevcut durumda veritabanında bulunan İşaretli Sorgular daha sonra tekrar veritbanına yüklenebilecek biçimde dosyaya kaydedilir.
public void saveOldMarkedQueriesToFile(String fileName) {
	StatementResult result;
	Record record;
		
		Session womqtd = AkkaSystem.driver.session();
		try(BufferedWriter bw = new BufferedWriter(new FileWriter(fileName))){
			
			for (int i = 1;i<11;i++) {
				result = womqtd.run("match ()-[a:ALIGNS]-() where a.alignmentNumber = '"+i+"' return distinct a.markedQuery,a.alignmentIndex");
				while(result.hasNext()){
					record = result.next();
					bw.write("AlignmentIndex:"+record.get(1).asString()+" ");
					if(!record.get(0).isNull())
					for (Object o : record.get(0).asList()) {
						bw.write((String) o+" ");
						System.out.println((String) o);
					}
					bw.newLine();
				}
				
			}	
			
		} catch(Exception e){
			e.printStackTrace();
			System.err.println("Write Old Marked Queries::: "+e.getMessage());
			unmarkAllConservedStructureQueries() ;
		} finally {womqtd.close();System.out.println("Old Marked Queries has been written to file: "+fileName);}
}


// Daha olmadi
public SubGraph convertMarkToSubGraph(AlignerImpl a, String markNumber){
	StatementResult result;
	Record record;
	SubGraph sg = new SubGraph(a);
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
	{
	result = tx.run("MATCH (n:Organism2) WHERE ANY(x IN n.markedQuery WHERE x = '"+markNumber+"') return distinct (n)");
	while(result.hasNext()){
		record = result.next();
		sg.nodes2.add(record.get(0).asNode());
	}
	
	record = null; result = null;
	result = tx.run("MATCH (n:Organism1) WHERE ANY(x IN n.markedQuery WHERE x = '"+markNumber+"') return distinct (n)");
	while(result.hasNext()){
		record = result.next();
		sg.nodes1.add(record.get(0).asNode());
	}
	
	record = null; result = null;
	result = tx.run("MATCH ()-[n:ALIGNS]-() WHERE ANY(x IN n.markedQuery WHERE x = '"+markNumber+"') return distinct (n)");
	while(result.hasNext()){
		record = result.next();
		sg.aligns.add(record.get(0).asRelationship());
	}
	
	record = null; result = null;
	result = tx.run("MATCH ()-[n:SIMILARITY]-() WHERE ANY(x IN n.markedQuery WHERE x = '"+markNumber+"') return distinct (n)");
	while(result.hasNext()){
		record = result.next();
		sg.similarity.add(record.get(0).asRelationship());
	}
	
	record = null; result = null;
	result = tx.run("MATCH ()-[n:INTERACTS_2]-() WHERE ANY(x IN n.markedQuery WHERE x = '"+markNumber+"') return distinct (n)");
	while(result.hasNext()){
		record = result.next();
		sg.interactions2.add(record.get(0).asRelationship());
	}
	
	record = null; result = null;
	result = tx.run("MATCH ()-[n:INTERACTS_1]-() WHERE ANY(x IN n.markedQuery WHERE x = '"+markNumber+"') return distinct (n)");	
	while(result.hasNext()){
		record = result.next();
		sg.interactions1.add(record.get(0).asRelationship());
	}
	
	tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
	
	return sg;
}
	
/**
 * Herhangi bir sorgu çalıştırmak için
 **/
public void queryGraph(String queryString){
	System.out.println("QUERYING");
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
		StatementResult result = tx.run( queryString );   
		while ( result.hasNext() )
		    {
		      System.out.println(result.next().toString());
		    }
		    tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
}

	/**
	 * Bir ağdaki tüm Edgelerin sayısı. True olursa ilk ağdakilerin sayısı. False olursa ikinci ağdakilerin 
	 **/
	
	public int countAllEdgesOfANetwork(boolean firstOrSecond){
		int count = 0;
		StatementResult result;
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction()  )
		{
			if(firstOrSecond)
				{
				result = tx.run( "match (n:Organism1)-[r:INTERACTS_1]->(m:Organism1) return count(r)");
				}
			else
				{
				result = tx.run( "match (n:Organism2)-[r:INTERACTS_2]->(m:Organism2) return count(r)");
				}
			count = Integer.parseInt(result.single().get("count(r)").toString());
			//result.close();
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
		
		return count;
	}
	
	public int countAllEdgesOfANetwork(boolean firstOrSecond, String markedQuery){
		int count = 0;
		StatementResult result;
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction()  )
		{
			if(firstOrSecond)
				{
				result = tx.run( "match (n:Organism1)-[r:INTERACTS_1]->(m:Organism1) where ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') and ANY(x IN m.markedQuery WHERE x = '"+markedQuery+"') return count(r)");
				}
			else
				{
				result = tx.run( "match (n:Organism2)-[r:INTERACTS_2]->(m:Organism2) where ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') and ANY(x IN m.markedQuery WHERE x = '"+markedQuery+"') return count(r)");
				}
			count = Integer.parseInt(result.single().get("count(r)").toString());
			//result.close();
			tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
		
		return count;
	}
	
	
	/**
	 * Hizalanan Düğümlerin Sayısı. OptNetAlign'da Size hedefine denk düşüyor.
	 * 
	 **/	
	public int countAlignedNodes(String alignmentNumber){
		Session can = driver.session();
		int count = 0;
		StatementResult result;
		
		try 
	      {
			result = can.run( "match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where r.alignmentNumber = '"+alignmentNumber+"'  return count(n)");
			count = Integer.parseInt(result.single().get("count(n)").toString());

	      } catch (Exception e){
	    	  e.printStackTrace();
	      } finally {can.close();}
		return count;
	}
	
public int countAlignedNodes(String alignmentNumber, String markedQuery){
		Session can = driver.session();
		int count = 0;
		StatementResult result;
		
		try 
	      {
			result = can.run( "match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where r.alignmentNumber = '"+alignmentNumber+"' "
			+ "and ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') and ANY(x IN m.markedQuery WHERE x = '"+markedQuery+"') return count(n)");
			count = Integer.parseInt(result.single().get("count(n)").toString());

	      } catch (Exception e){
	    	  e.printStackTrace();
	      } finally {can.close();}
		return count;
	}
	
	/**
	 * EC, ICS ve S3 hesaplamak için kullanılan hizalı köşelerin toplam sayısı
	 *  
	 **/	
	public int countAlignedEdges(String alignmentNumber){
		Session cae = driver.session();
		int count = 0;
		StatementResult result;
		
		try 
	      {
			result = cae.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[t:ALIGNS]->(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)<-[s:ALIGNS]-(o) where s.alignmentNumber = '"+alignmentNumber+"' and t.alignmentNumber = '"+alignmentNumber+"' return count(distinct u)");
			count = Integer.parseInt(result.single().get("count(distinct u)").toString());
	      } catch (Exception e){
	    	  e.printStackTrace();
	      } finally {cae.close();}
		return count;
	}
	
	public int countAlignedEdges(String alignmentNumber, String markedQuery){
		Session cae = driver.session();
		int count = 0;
		StatementResult result;
		
		try 
	      {
			result = cae.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[t:ALIGNS]->(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)<-[s:ALIGNS]-(o) where s.alignmentNumber = '"+alignmentNumber+"' and t.alignmentNumber = '"+alignmentNumber+"' "
					+ "and ANY(x IN o.markedQuery WHERE x = '"+markedQuery+"') and ANY(x IN p.markedQuery WHERE x = '"+markedQuery+"') and ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') and ANY(x IN m.markedQuery WHERE x = '"+markedQuery+"') return count(distinct u)");
			
			count = Integer.parseInt(result.single().get("count(distinct u)").toString());

	      } catch (Exception e){
	    	  e.printStackTrace();
	      } finally {cae.close();}
		return count;
	}
	
	public int countAlignedEdgesWithDirection(String alignmentNumber, boolean direction){
		Session caewd = driver.session();
		int count = 0;
		StatementResult result;

		try 
		{
		if(direction){
		//o->p ve n->m etkileşim desenlerine uyan hizalı köşelerin toplam sayısı
		result = caewd.run( "match (o)-[u:INTERACTS_2]->(p)-[t:ALIGNS]->(n)-[r:INTERACTS_1]->(m)<-[s:ALIGNS]-(o) where s.alignmentNumber = "+alignmentNumber+" and t.alignmentNumber = "+alignmentNumber+" return count(n)");
		} else {
		//o->p ve m->n etkileşim desenlerine uyan hizalı köşelerin toplam sayısı
		result = caewd.run( "match (o)-[u:INTERACTS_2]->(p)-[t:ALIGNS]->(n)<-[r:INTERACTS_1]-(m)<-[s:ALIGNS]-(o) where s.alignmentNumber = "+alignmentNumber+" and t.alignmentNumber = "+alignmentNumber+" return count(n)");
		}
		
		count = Integer.parseInt(result.single().get("count(n)").toString());

		} catch (Exception e){
		  e.printStackTrace();
		} finally {caewd.close();}
		return count;
		}
	
/*
 * Bi de bu vard� bi zamanlar. Induced Graph taki d���mlerin yapt��� hizalanmam�� kenarlar� da say�yor.
 * 	if(firstOrSecond) result = tx.run( "match (o)-[t:ALIGNS]->(n) where t.alignmentNumber = '"+alignmentNumber+"' optional match (n)-[r:INTERACTS_1]->(m) return count(r)");
	else result = tx.run( "match (o)<-[t:ALIGNS]-(n) where t.alignmentNumber = '"+alignmentNumber+"' optional match (n)-[r:INTERACTS_2]->(m) return count(r)");
 * 
 * */

/**
 * ICS ve S3 hesaplamak için. Denominator of ICS
 * 
 **/	
public int countAlignedEdges1(String alignmentNumber){
	Session cae1 = driver.session();
	int count = 0;
	StatementResult result;
	
	try
      {
		result = cae1.run( "match (p:Organism2)-[t:ALIGNS]->(n:Organism1)-[r:INTERACTS_1]->(m:Organism1)<-[s:ALIGNS]-(o:Organism2) where s.alignmentNumber = '"+alignmentNumber+"' and t.alignmentNumber = '"+alignmentNumber+"' return count(r)");
		
		count = Integer.parseInt(result.single().get("count(r)").toString());

      } catch (Exception e){
    	  e.printStackTrace();
      } finally {cae1.close();}
	return count;
}

public int countAlignedEdges1(String alignmentNumber, String markedQuery){
	Session cae1 = driver.session();
	int count = 0;
	StatementResult result;
	
	try 
      {
		result = cae1.run( "match (p:Organism2)-[t:ALIGNS]->(n:Organism1)-[r:INTERACTS_1]->(m:Organism1)<-[s:ALIGNS]-(o:Organism2) where s.alignmentNumber = '"+alignmentNumber+"' and t.alignmentNumber = '"+alignmentNumber+"' "
				+ "and ANY(x IN o.markedQuery WHERE x = '"+markedQuery+"') and ANY(x IN p.markedQuery WHERE x = '"+markedQuery+"') and ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') and ANY(x IN m.markedQuery WHERE x = '"+markedQuery+"') return count(r)");
		
		count = Integer.parseInt(result.single().get("count(r)").toString());

      } catch (Exception e){
    	  e.printStackTrace();
      } finally {cae1.close();}
	return count;
}

/**
 * ICS ve S3 hesaplamak için
 * 
 **/	
public int countAlignedEdges2(String alignmentNumber){
	Session cae2 = driver.session();
	int count = 0;
	StatementResult result;
	
	try 
      {
		result = cae2.run( "match (p:Organism1)<-[t:ALIGNS]-(n:Organism2)-[r:INTERACTS_2]->(m:Organism2)-[s:ALIGNS]->(o:Organism1) where s.alignmentNumber = '"+alignmentNumber+"' and t.alignmentNumber = '"+alignmentNumber+"' return count(r)");
		
		count = Integer.parseInt(result.single().get("count(r)").toString());

      } catch (Exception e){
    	  e.printStackTrace();
      } finally {cae2.close();}
	return count;
}

public int countAlignedEdges2(String alignmentNumber, String markedQuery){
	Session cae2 = driver.session();
	int count = 0;
	StatementResult result;
	
	try 
      {
		result = cae2.run( "match (p:Organism1)<-[t:ALIGNS]-(n:Organism2)-[r:INTERACTS_2]->(m:Organism2)-[s:ALIGNS]->(o:Organism1) where s.alignmentNumber = '"+alignmentNumber+"' and t.alignmentNumber = '"+alignmentNumber+"' "
				+ "and ANY(x IN o.markedQuery WHERE x = '"+markedQuery+"') and ANY(x IN p.markedQuery WHERE x = '"+markedQuery+"') and ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') and ANY(x IN m.markedQuery WHERE x = '"+markedQuery+"') return count(r)");
		
		count = Integer.parseInt(result.single().get("count(r)").toString());

      } catch (Exception e){
    	  e.printStackTrace();
      } finally {cae2.close();}
	return count;
}


public int countInducedSubGraphEdgesOfANetwork(boolean firstOrSecond, String alignmentNumber){
	int count = 0;

		if(firstOrSecond)
			{
			count = countAlignedEdges2(alignmentNumber);
			}
		else
			{
			count = countAlignedEdges1(alignmentNumber);
			}

	return count;
}

public int countInducedSubGraphEdgesOfANetwork(boolean firstOrSecond, String alignmentNumber, String markedQuery){
	int count = 0;

		if(firstOrSecond)
			{
			count = countAlignedEdges2(alignmentNumber, markedQuery);
			}
		else
			{
			count = countAlignedEdges1(alignmentNumber, markedQuery);
			}

	return count;
}

public double calculateBitScoreSum(String alignmentNumber){
	Session cbs = driver.session();
	double sum = 0.0;
	StatementResult result;
	
	try 
    {
		result = cbs.run("match (n:Organism1)<-[r:ALIGNS]-(m:Organism2)<-[s:SIMILARITY]-(n) where r.alignmentNumber = '"+alignmentNumber+"' return sum(s.similarity)");
		
		sum = Double.parseDouble(result.single().get("sum(s.similarity)").toString());

    } catch (Exception e){
  	  e.printStackTrace();
    }	finally {cbs.close();}
	return sum;
}

public double calculateBitScoreSum(String alignmentNumber, String markedQuery){
	Session cbs = driver.session();
	double sum = 0.0;
	StatementResult result;
	
	try 
    {
		result = cbs.run("match (n:Organism1)<-[r:ALIGNS]-(m:Organism2)<-[s:SIMILARITY]-(n) where r.alignmentNumber = '"+alignmentNumber+"' and ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') and ANY(x IN m.markedQuery WHERE x = '"+markedQuery+"') return sum(s.similarity)");
		
		sum = Double.parseDouble(result.single().get("sum(s.similarity)").toString());

    } catch (Exception e){
  	  e.printStackTrace();
    }	finally {cbs.close();}
	return sum;
}

/**
 * OPtNetALign'daki GOC hesaplama yöntemi: Kesişim/Birleşim
 * 
 **/
public double calculateGOCScore(String alignmentNumber){
	Session cgcs = driver.session();
	double totalScore = 0.0;
	StatementResult result;
	int size1;
	int size2;
	int intersection;
	
	try 
    {
		result =cgcs.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where r.alignmentNumber ='"+alignmentNumber+"' return size(n.annotations),size(m.annotations),length(FILTER(x in n.annotations WHERE x in m.annotations))");
		while(result.hasNext()){
			Record row = result.next();
			size1 = 0;
			size2 = 0;
			intersection = 0;
			
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null)
					switch (column.getKey()) {
					case "size(m.annotations)":
						size1 = Integer.parseInt(column.getValue().toString());
						break;
					case "length(FILTER(x in n.annotations WHERE x in m.annotations))":
						intersection = Integer.parseInt(column.getValue().toString());
						break;
					case "size(n.annotations)":
						size2 = Integer.parseInt(column.getValue().toString());
						break;
					default:
						System.out.println("Unexpected column");
						break;
					}
				//System.out.print(column.getKey()+": "+Integer.parseInt(column.getValue().toString())+"-");
			}
			if(size1!=0&&size2!=0)
				totalScore += intersection/(double)(size1+size2-intersection);
			// Birbiriyle aynı olan k adet Gen Ontolojisi terimine sahip Proteinlerin sayısı aşağıdaki metotla bulunacak
			//countGOCProteins(String alignmentNumber, int k)
			/*o = result.columnAs("length(FILTER(x in n.annotations WHERE x in m.annotations))").next();
			if (o!=null)
				if (k<=new Integer(o.toString()).intValue())
					{
					count++;
					System.out.println(o.toString());
					}*/
		}

    } catch (Exception e){
    	  e.printStackTrace();
      } finally {cgcs.close();}
	//System.out.println(totalScore);
	return totalScore;
}

public double calculateGOCScore(String alignmentNumber, String markedQuery){
	Session cgcs = driver.session();
	double totalScore = 0.0;
	StatementResult result;
	int size1;
	int size2;
	int intersection;
	try 
    {
		result = cgcs.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where r.alignmentNumber ='"+alignmentNumber+"' and ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') and ANY(x IN m.markedQuery WHERE x = '"+markedQuery+"') return size(n.annotations),size(m.annotations),length(FILTER(x in n.annotations WHERE x in m.annotations))");
		while(result.hasNext()){
			Record row = result.next();
			size1 = 0;
			size2 = 0;
			intersection = 0;
			
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null)
					switch (column.getKey()) {
					case "size(m.annotations)":
						size1 = Integer.parseInt(column.getValue().toString());
						break;
					case "length(FILTER(x in n.annotations WHERE x in m.annotations))":
						intersection = Integer.parseInt(column.getValue().toString());
						break;
					case "size(n.annotations)":
						size2 = Integer.parseInt(column.getValue().toString());
						break;
					default:
						System.out.println("Unexpected column");
						break;
					}
			}
			if(size1!=0&&size2!=0)
				totalScore += intersection/(double)(size1+size2-intersection);
		}

    } catch (Exception e){
    	  e.printStackTrace();
      } finally {cgcs.close();}
	return totalScore;
}
// Hizalamanın boyutuna bölmek yerine doğrudan ortak anotasyona sahip protein çifti sayısı kullanılabilir.
public int calculateGOEnrichment(String alignmentNumber) {
	Session cgoe = driver.session();
	StatementResult result;
	int count = 0;
	try 
    {
		result =cgoe.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where r.alignmentNumber ='"+alignmentNumber+"' and length(FILTER(x in n.annotations WHERE x in m.annotations))>=1 return count(r)");
		count = Integer.parseInt(result.single().get("count(r)").toString());
    } catch (Exception e){
    	  e.printStackTrace();
      } finally {cgoe.close();}
	return count;
}

public int calculateLCCS(String alignmentNumber) {
	Session clccs = driver.session();
	StatementResult result;
	int count = 0;
	int cluster2 = 0;
	int cluster1 = 0;
	try 
    {
		clccs.run("MATCH (p) REMOVE p.lccs2 return (p)");
		clccs.run("MATCH (p) REMOVE p.lccs1 return (p)");
		clccs.run("CALL algo.unionFind(\n" + 
				"  'MATCH (p:Organism2)-[r:ALIGNS {alignmentNumber:"+alignmentNumber+" }]->() RETURN id(p) as id',\n" + 
				"  'MATCH (p1:Organism2)-[:INTERACTS_2]-(p2:Organism2) RETURN id(p1) as source, id(p2) as target',\n" + 
				"  {write: true, writeProperty:'lccs2'}\n" + 
				");");
		result = clccs.run("match (n:Organism2) with distinct(n.lccs2) as clusterid, count(n) as clustersize return clusterid order by clustersize desc limit 1");
		cluster2 = Integer.parseInt(result.single().get("clusterid").toString());
		System.out.println("geldi mi: "+cluster2);

		clccs.run("CALL algo.unionFind(\n" + 
				"  'MATCH ()-[r:ALIGNS {alignmentNumber:"+alignmentNumber+" }]->(q:Organism1) RETURN id(q) as id',\n" + 
				"  'MATCH (p1:Organism1)-[:INTERACTS_1]-(p2:Organism1) RETURN id(p1) as source, id(p2) as target',\n" + 
				"  {write: true, writeProperty:'lccs1'}\n" + 
				");");
		result = clccs.run("match (n:Organism1) with distinct(n.lccs1) as clusterid, count(n) as clustersize return clusterid order by clustersize desc limit 1");
		cluster1 = Integer.parseInt(result.single().get("clusterid").toString());
		
		System.out.println(cluster1);
		
		System.out.println(cluster2);
		result = clccs.run("match (p:Organism1)<-[r:ALIGNS]-(q:Organism2) where p.lccs1 ="+cluster1+" and q.lccs2 = "+cluster2+" and  r.alignmentNumber ='"+alignmentNumber+"' return count(distinct(q)) as lccs");
		count = Integer.parseInt(result.single().get("lccs").toString());
    } catch (Exception e){
    	  e.printStackTrace();
      } finally {clccs.close();}
	return count;
}

public void calculateGlobalBenchmarks(String alignmentNo){
	System.out.println("Global Benchmark Scores for alignment "+alignmentNo);
	System.out.println("EC: "+(double) this.countAlignedEdges(alignmentNo)/(double) this.sizeOfSecondNetwork+" aligned edges: "+this.countAlignedEdges(alignmentNo)+" all edges: "+this.countAllEdgesOfANetwork(false));
	System.out.println("ICS: "+(double) this.countAlignedEdges(alignmentNo)/(double) this.countInducedSubGraphEdgesOfANetwork(false,alignmentNo)+" aligned edges: "+this.countAlignedEdges(alignmentNo)+" induced edges: "+this.countInducedSubGraphEdgesOfANetwork(false,alignmentNo));
	System.out.println("S^3: "+(double) this.countAlignedEdges(alignmentNo)/((double) this.sizeOfSecondNetwork + (double) this.countInducedSubGraphEdgesOfANetwork(false,alignmentNo) - (double) this.countAlignedEdges(alignmentNo)));
	System.out.println("GOC: "+this.calculateGOCScore(alignmentNo));
	System.out.println("BitScore: "+this.calculateBitScoreSum(alignmentNo));
	System.out.println("Size: "+this.countAlignedNodes(alignmentNo));
}

public BenchmarkScores calculateGlobalBenchmarks(Aligner a){
//	System.out.println("işte karşınızda "+a.getBenchmarkScores());
	TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
	tm.failure(new Throwable("Herkesin tuttuğu kendine"));
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
	BenchmarkScores finished = template.with(AkkaSystem.graphDb).execute( transaction -> {
		BenchmarkScores bs= a.getBenchmarkScores();
		try {
			bs.setEC((double) this.countAlignedEdges(Integer.toString(a.getAlignmentNo()))/(double) this.sizeOfSecondNetwork);
			bs.setICS((double) this.countAlignedEdges(Integer.toString(a.getAlignmentNo()))/(double) this.countInducedSubGraphEdgesOfANetwork(false,Integer.toString(a.getAlignmentNo())));
			bs.setS3((double) this.countAlignedEdges(Integer.toString(a.getAlignmentNo()))/((double) this.sizeOfSecondNetwork + (double) this.countInducedSubGraphEdgesOfANetwork(false,Integer.toString(a.getAlignmentNo())) - (double) this.countAlignedEdges(Integer.toString(a.getAlignmentNo()))));
			bs.setLCCS(this.calculateLCCS(Integer.toString(a.getAlignmentNo())));
			bs.setGOC(this.calculateGOCScore(Integer.toString(a.getAlignmentNo())));
			bs.setBitScore(this.calculateBitScoreSum(Integer.toString(a.getAlignmentNo())));
			bs.setSize(this.countAlignedNodes(Integer.toString(a.getAlignmentNo())));
			
			if (a.getBenchmarkScores().EC>bestEC) {bestEC = a.getBenchmarkScores().EC;alignerWithBestEC = a.getAlignmentNo();}
			if (a.getBenchmarkScores().ICS>bestICS) {bestICS = a.getBenchmarkScores().ICS;alignerWithBestICS = a.getAlignmentNo();}
			if (a.getBenchmarkScores().S3>bestS3) {bestS3 = a.getBenchmarkScores().S3;alignerWithBestS3 = a.getAlignmentNo();}
			if (a.getBenchmarkScores().GOC>bestGOC) {bestGOC = a.getBenchmarkScores().GOC;alignerWithBestGOC = a.getAlignmentNo();}
			if (a.getBenchmarkScores().bitScore>bestBitscore) {bestBitscore = a.getBenchmarkScores().bitScore;alignerWithBestBitscore = a.getAlignmentNo();}
			if (a.getBenchmarkScores().size>bestSize) {bestSize = a.getBenchmarkScores().size;alignerWithBestSize = a.getAlignmentNo();}
			System.out.println("işte karşınızda "+bs);
			
			
			try(FileWriter fw = new FileWriter("calculateGlobalBenchmarks"+a.getAlignmentNo()+".txt", true);
				    BufferedWriter bw = new BufferedWriter(fw);
				    PrintWriter out = new PrintWriter(bw))
				{
					out.println(bs);
				} catch (IOException e) {
				    //exception handling left as an exercise for the reader
				}
			
			System.out.println("EC birincisi: "+alignerWithBestEC+", ICS birincisi: "+alignerWithBestICS+", S3 birincisi: "+alignerWithBestS3+", GOC birincisi: "+alignerWithBestGOC+", BitScore birincisi: "+alignerWithBestBitscore+", en büyük: "+alignerWithBestSize);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			System.out.println("calculateGlobalBenchmarks: "+e.getMessage());
		}
		return bs;
	} );
	return finished;
}

public void calculateSubGraphBenchmarks(String alignmentNo, String markedQuery){
	System.out.println("Local Benchmark Scores for alignment "+alignmentNo+" and SubGraph: "+markedQuery);
	System.out.println("EC: "+(double) this.countAlignedEdges(alignmentNo,markedQuery)/(double) this.countAllEdgesOfANetwork(false,markedQuery)+" aligned edges: "+this.countAlignedEdges(alignmentNo,markedQuery)+" all edges: "+this.countAllEdgesOfANetwork(false,markedQuery));
	System.out.println("ICS: "+(double) this.countAlignedEdges(alignmentNo,markedQuery)/(double) this.countInducedSubGraphEdgesOfANetwork(false,alignmentNo,markedQuery)+" aligned edges: "+this.countAlignedEdges(alignmentNo,markedQuery)+" induced edges: "+this.countInducedSubGraphEdgesOfANetwork(false,alignmentNo,markedQuery));
	System.out.println("S^3: "+(double) this.countAlignedEdges(alignmentNo,markedQuery)/((double) this.countAllEdgesOfANetwork(false,markedQuery) + (double) this.countInducedSubGraphEdgesOfANetwork(false,alignmentNo,markedQuery) - (double) this.countAlignedEdges(alignmentNo,markedQuery)));
	System.out.println("GOC: "+this.calculateGOCScore(alignmentNo,markedQuery));
	System.out.println("BitScore: "+this.calculateBitScoreSum(alignmentNo,markedQuery));
	System.out.println("Size: "+this.countAlignedNodes(alignmentNo,markedQuery));
}

public BenchmarkScores calculateSubGraphBenchmarks(Aligner a, String markedQuery){
//	System.out.println("işte karşınızda "+a.getBenchmarkScores());
	TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
	tm.failure(new Throwable("Herkesin tuttuğu kendine"));
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
	BenchmarkScores finished = template.with(AkkaSystem.graphDb).execute( transaction -> {
		BenchmarkScores bs= new BenchmarkScores(a.getAlignmentNo(),Long.valueOf(markedQuery));
		try {
			bs.setEC((double) this.countAlignedEdges(Integer.toString(a.getAlignmentNo()),markedQuery)/(double) this.countAllEdgesOfANetwork(false,markedQuery));
			bs.setICS((double) this.countAlignedEdges(Integer.toString(a.getAlignmentNo()),markedQuery)/(double) this.countInducedSubGraphEdgesOfANetwork(false,Integer.toString(a.getAlignmentNo()),markedQuery));
			bs.setS3((double) this.countAlignedEdges(Integer.toString(a.getAlignmentNo()),markedQuery)/((double) this.countAllEdgesOfANetwork(false,markedQuery) + (double) this.countInducedSubGraphEdgesOfANetwork(false,Integer.toString(a.getAlignmentNo()),markedQuery) - (double) this.countAlignedEdges(Integer.toString(a.getAlignmentNo()),markedQuery)));
			bs.setGOC(this.calculateGOCScore(Integer.toString(a.getAlignmentNo()),markedQuery));
			bs.setBitScore(this.calculateBitScoreSum(Integer.toString(a.getAlignmentNo()),markedQuery));
			bs.setSize(this.countAlignedNodes(Integer.toString(a.getAlignmentNo()),markedQuery));
			
			try(FileWriter fw = new FileWriter("calculateGlobalBenchmarks"+a.getAlignmentNo()+".txt", true);
				    BufferedWriter bw = new BufferedWriter(fw);
				    PrintWriter out = new PrintWriter(bw))
				{
					out.println(bs);
				} catch (IOException e) {
				    //exception handling left as an exercise for the reader
				}
			
			System.out.println("işte karşınızda "+bs);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			System.out.println("calculateSubGraphBenchmarks: "+e.getMessage());
		}
		return bs;
	} );
	
	return finished;
}

public Aligner getAligner(int alignerNo) {
	for (Aligner aligner : routees) {
		if (aligner.getAlignmentNo() == alignerNo)
			return aligner;
	}
	System.err.println("The alignment could not be retrieved by its number");
	return null;
}

public Cancellable markBestSubGraphsInTime(int initialDelay, int interval) {
	

Timeout sg =  new Timeout(Duration.create(180, "seconds"));

	Cancellable c = AkkaSystem.system2.scheduler().schedule((FiniteDuration) Duration.create(initialDelay+" seconds"),(FiniteDuration) Duration.create(interval+" seconds"),new Runnable() {
		@Override
		public void run() {
			
			Aligner bs = getAligner(alignerWithBestBitscore);
			if(bs != null)
			bs.setMesaj("BS");
			Aligner ec = getAligner(alignerWithBestEC);
			if(ec != null)
			ec.setMesaj("EC");
			Aligner ics = getAligner(alignerWithBestICS);
			if(ics != null)
			ics.setMesaj("ICS");
			Aligner s3 = getAligner(alignerWithBestS3);
			if(s3 != null)
			s3.setMesaj("S3");
			Aligner goc = getAligner(alignerWithBestGOC);
			if(goc != null)
			goc.setMesaj("GOC");
			Aligner size = getAligner(alignerWithBestSize);
			if(size != null)
			size.setMesaj("SIZE");
			
			Long ecl = -1L;
			Long bsl = -1L;
			Long gocl = -1L;
			Long icsl = -1L;
			Long s3l = -1L;
			
			String centralityType = null;
			int  n = new Random().nextInt(4);
			if(n ==0)
				centralityType = "betweenness";
			else if (n==1)
				centralityType = "harmonic";
			else if (n==2)
				centralityType = "pagerank";
			else if (n==3)
				centralityType = "closeness";		
			
			try {
				ecl = Await.result(ec.markAlignedEdges(marked++, AkkaSystem.system2.dispatcher()),sg.duration());
//				if(bs.getBenchmarkScores().size>0)
				bsl = Await.result(bs.markXBitScoreSimilarity((bestBitscore+1)/bs.getBenchmarkScores().size, marked++,  AkkaSystem.system2.dispatcher()), sg.duration());
//				if(goc.getBenchmarkScores().size>0)
				gocl = Await.result(goc.markKGOTerms((int) bestGOC/goc.getBenchmarkScores().size, marked++, AkkaSystem.system2.dispatcher()), sg.duration());
				icsl = Await.result(ics.markTopAlignedPowerNodes(100,marked++, centralityType, AkkaSystem.system2.dispatcher()), sg.duration());
				
//				if(Math.random() < 0.8)
					s3l = Await.result(s3.markTopAlignedPowerNodes(100,marked++, "power", AkkaSystem.system2.dispatcher()),sg.duration());
//				else
//					s3l = Await.result(s3.markDoubleAlignedEdges(marked++,AkkaSystem.system2.dispatcher()),sg.duration());
//					s3l = Await.result(s3.markConservedStructureQuery(AkkaSystem.system2.dispatcher(), marked++),sg.duration());
			} catch (Exception e) {
				// TODO Auto-generated catch block
				System.err.println("ECL: "+ecl+" - BSL: "+bsl+" - GOCL: "+gocl+" - ICSL: "+icsl);
				markBestSubGraphsInTime(initialDelay, interval);
			}

		 // don’t forget to think about who is the sender (2nd argument)
		 // target.tell(message, getSelf());
	     //   router.tell(firstAligner.getBenchmarkScores(), as.typed.getActorRefFor(typedRouter));
			
		router.tell(ecl, ActorRef.noSender());
		router.tell(bsl, ActorRef.noSender());
		router.tell(gocl,ActorRef.noSender());
		router.tell(icsl,ActorRef.noSender());
		router.tell(s3l,ActorRef.noSender());
		
		// Bu aşağıdakilerden sadece bir tanesi rastgele seçilebilir ve işlem daha sık tekrarlanabilir.
//		 akka.pattern.Patterns.pipe(ec.markAlignedEdges(System.currentTimeMillis(), AkkaSystem.system2.dispatcher()), system2.dispatcher()).to(router);
//		 akka.pattern.Patterns.pipe(bs.markXBitScoreSimilarity(bestBitscore/bs.getBenchmarkScores().bitScore, System.currentTimeMillis(),  AkkaSystem.system2.dispatcher()), system2.dispatcher()).to(router);
//		 akka.pattern.Patterns.pipe(goc.markKGOTerms((int) bestGOC/goc.getBenchmarkScores().size, System.currentTimeMillis(), AkkaSystem.system2.dispatcher()), system2.dispatcher()).to(router);
//		 akka.pattern.Patterns.pipe(s3.markConservedStructureQuery(system2.dispatcher(), System.currentTimeMillis()), system2.dispatcher()).to(router);
//		 akka.pattern.Patterns.pipe(ics.markConservedStructureQuery(system2.dispatcher(), System.currentTimeMillis()), system2.dispatcher()).to(router);
				
		}
		}, AkkaSystem.system2.dispatcher());
	//Scheduler.schedule();
	return c;
}

public Cancellable markClusters(int initialDelay, int interval) {

	
Timeout sg =  new Timeout(Duration.create(60, "seconds"));

	Cancellable c = AkkaSystem.system2.scheduler().schedule((FiniteDuration) Duration.create(initialDelay+" seconds"),(FiniteDuration) Duration.create(interval+" seconds"),new Runnable() {
		@Override
		public void run() {
			Aligner s3 = AkkaSystem.this.getAligner(alignerWithBestS3);
			s3.setMesaj("S3");
			Long s3l = null;
			String clusterType;
			long clusterIDOfOrganism1 = 0;
			long clusterIDOfOrganism2 = 0;
			try {
				
				boolean toss = Math.random() < 0.5;
				boolean toss2 = Math.random() < 0.5;
				// Sıralamanın ilk sırasındaki sıfırıncılar yerine diğer ön sıralardakiler de rastgele denenebilir.
				if(toss) {
					clusterType = "louvain";
					if(toss2) {
						clusterIDOfOrganism1 = csLouvainBitScore.get(0).clusterIDOfOrganism1;
						clusterIDOfOrganism2 = csLouvainBitScore.get(0).clusterIDOfOrganism2;
					} else {
						clusterIDOfOrganism1 = csLouvainGO.get(0).clusterIDOfOrganism1;
						clusterIDOfOrganism2 = csLouvainGO.get(0).clusterIDOfOrganism2;
					}		
				}
				else {
					clusterType = "labelpropagation";
					if(toss2) {
						clusterIDOfOrganism1 = csLabelPropagationBitScore.get(0).clusterIDOfOrganism1;
						clusterIDOfOrganism2 = csLabelPropagationBitScore.get(0).clusterIDOfOrganism2;
					} else {
						clusterIDOfOrganism1 = csLabelPropagationGO.get(0).clusterIDOfOrganism1;
						clusterIDOfOrganism2 = csLabelPropagationGO.get(0).clusterIDOfOrganism2;
					}
				}
				s3l = Await.result(s3.markClusterEdges(1, clusterType, clusterIDOfOrganism1, clusterIDOfOrganism2, System.currentTimeMillis(), AkkaSystem.system2.dispatcher()),sg.duration());
			} catch (Exception e) {
				// TODO Auto-generated catch block
//				System.err.println("ECL: "+ecl+" - BSL: "+bsl+" - GOCL: "+gocl+" - ICSL: "+icsl);
			}
		router.tell(s3l, ActorRef.noSender());
				
		}
		}, AkkaSystem.system2.dispatcher());
	//Scheduler.schedule();
	return c;
}

public Cancellable markPowerAlignments(int initialDelay, int interval) {
	Aligner s3 = this.getAligner(alignerWithBestS3);
	s3.setMesaj("S3");
Timeout sg =  new Timeout(Duration.create(60, "seconds"));

	Cancellable c = AkkaSystem.system2.scheduler().schedule((FiniteDuration) Duration.create(initialDelay+" seconds"),(FiniteDuration) Duration.create(interval+" seconds"),new Runnable() {
		@Override
		public void run() {
			for (Aligner aligner : routees) {
				Long s3l = null;
				try {
					s3l = Await.result(aligner.markAlignedPowerNodes(1, 10, 5, 5, System.currentTimeMillis(), AkkaSystem.system2.dispatcher()),sg.duration());
				} catch (Exception e) {
					// TODO Auto-generated catch block
				}
			router.tell(s3l, ActorRef.noSender());
			}
		}
		}, AkkaSystem.system2.dispatcher());
	//Scheduler.schedule();
	return c;
}

public Cancellable markCentralNodeAlignments() {
	return null;
}

public Cancellable sendBestBenchmarkScoresInTime(int initialDelay, int interval) {
	Cancellable c = AkkaSystem.system2.scheduler().schedule((FiniteDuration) Duration.create(initialDelay+" seconds"),(FiniteDuration) Duration.create(interval+" seconds"),new Runnable() {
		@Override
		public void run() {
			for (Aligner aligner : routees) {
				router.tell(aligner.getBenchmarkScores(), typed.getActorRefFor(aligner));
				// EC SUBGRAPHI BİR DE BURDAN YOLLANABİLİR
			}

		}
		}, AkkaSystem.system2.dispatcher());
	//Scheduler.schedule();
	
	return c;
}

public Cancellable writeBestAlignmentsInTime(int initialDelay, int interval) {
	

	
	Cancellable c = AkkaSystem.system2.scheduler().schedule((FiniteDuration) Duration.create(initialDelay+" seconds"),(FiniteDuration) Duration.create(interval+" seconds"),new Runnable() {
		@Override
		public void run() {
			
			Aligner bs = AkkaSystem.this.getAligner(alignerWithBestBitscore);
			Aligner ec = AkkaSystem.this.getAligner(alignerWithBestEC);
			Aligner ics = AkkaSystem.this.getAligner(alignerWithBestICS);
			Aligner s3 = AkkaSystem.this.getAligner(alignerWithBestS3);
			Aligner goc = AkkaSystem.this.getAligner(alignerWithBestGOC);
			Aligner size = AkkaSystem.this.getAligner(alignerWithBestSize);

			ec.writeAlignmentToDisk("EC"+System.currentTimeMillis());
			bs.writeAlignmentToDisk("BS"+System.currentTimeMillis());
			ics.writeAlignmentToDisk("ICS"+System.currentTimeMillis());
			s3.writeAlignmentToDisk("S3"+System.currentTimeMillis());
			goc.writeAlignmentToDisk("GOC"+System.currentTimeMillis());
			size.writeAlignmentToDisk("SIZE"+System.currentTimeMillis());
			
		}
		}, AkkaSystem.system2.dispatcher());
	//Scheduler.schedule();
	
	return c;
}

public Cancellable removeManyToManyAlignments(int initialDelay, int interval) {
	
	Cancellable c = AkkaSystem.system2.scheduler().schedule((FiniteDuration) Duration.create(initialDelay+" seconds"),(FiniteDuration) Duration.create(interval+" seconds"),new Runnable() {
		@Override
		public void run() {
			
			boolean toss = Math.random() < 0.5;
			if(toss)
				removeLatterOfManyToManyAlignments();
			else
				removeWeakerOfManyToManyAlignments();
		}
		}, AkkaSystem.system2.dispatcher());
	//Scheduler.schedule();
	
	return c;
}
//  rastgele seçmek ya da Alt Çizge Kıyaslama Ölçütleri Yüksek bir tanesini seçmek gibi iki alternatif var.
public Cancellable retryPreviouslyMarkedQueries(int initialDelay, int interval) {
	Cancellable c = AkkaSystem.system2.scheduler().schedule((FiniteDuration) Duration.create(initialDelay+" seconds"),(FiniteDuration) Duration.create(interval+" seconds"),new Runnable() {
		@Override
		public void run() {
	
			Long previousQuery = 0L;	
			Random rand;
			for (Aligner aligner : routees) {
				rand = new Random();
				if(aligner.getMarkedQueries().size()>=1) {
					previousQuery = Long.valueOf(aligner.getMarkedQueries().get(rand.nextInt(aligner.getMarkedQueries().size())).markedQuery);
					router.tell(previousQuery, typed.getActorRefFor(aligner));
					System.err.println("Previous Query No: "+previousQuery+" is sent to router.");
				}
			}
		}
		}, AkkaSystem.system2.dispatcher());
	//Scheduler.schedule();
	
	return c;
}
// Alt Çizge Kıyaslama Ölçütleri Yüksek bir tanesini seçme alternatifi.
// BenchmarkScorelar AlignerImpl içindeki markAlignedEdges ve benzeri metotlar içinde hesaplanabilir ama orada hesaplanırsa birden fazla defa hesaplanma ihtimali var.
// Alternatifi burada belirli bir sıklıkla hesaplamak
public Cancellable retryStrongerPreviouslyMarkedQueries(int initialDelay, int interval) {
	Cancellable c = AkkaSystem.system2.scheduler().schedule((FiniteDuration) Duration.create(initialDelay+" seconds"),(FiniteDuration) Duration.create(interval+" seconds"),new Runnable() {
		@Override
		public void run() {
			BenchmarkScores temp = new BenchmarkScores();
			for (Aligner aligner : routees) 
				for (BenchmarkScores bs : aligner.getMarkedQueries()) {
					if (bs.countOfBetterBenchmarks(temp) > 3)
						temp = bs;
				router.tell(Long.valueOf(temp.markedQuery), typed.getActorRefFor(aligner));
				}
		}
		}, AkkaSystem.system2.dispatcher());
	//Scheduler.schedule();
	
	return c;
}

public Cancellable addRandomMapping(int initialDelay, int interval) {
	Cancellable c = AkkaSystem.system2.scheduler().schedule((FiniteDuration) Duration.create(initialDelay+" seconds"),(FiniteDuration) Duration.create(interval+" seconds"),new Runnable() {
		@Override
		public void run() {
			for (int i = 0;i<routees.size();i++) 
				{
				
				if(routees.get(i).getAlignmentNo()==1) {
					Random rand1 = new Random();
					Random rand2 = new Random();
					int  n = rand1.nextInt((int)Math.floor(AkkaSystem.this.averageCommonAnnotations/2));
					double s = rand2.nextInt(4)*AkkaSystem.this.averageSimilarity/4;
					if(Math.random() < 0.5)
						routees.get(i).alignCentralPowerNodesFromTop(n, s+1, '3', 0.3,1000, '3');
					else
						routees.get(i).alignCentralPowerNodesFromTop(n, s+1, '4', 0.3,1000, '3');
					if(Math.random() < 0.5)
						routees.get(i).increaseECByAddingPair(n, s, '3');
					else
						routees.get(i).increaseECByAddingPair((int)Math.floor(n/2), Math.floor(s/2), '3');
					
				}
					
					if(routees.get(i).getAlignmentNo()==2) {
						Random rand1 = new Random();
						Random rand2 = new Random();
						Random rand3 = new Random();
						Random rand4 = new Random();
						Random rand5 = new Random();
						int  n = rand1.nextInt((int)Math.floor(AkkaSystem.this.averageCommonAnnotations));
						double s = rand2.nextInt(4)*AkkaSystem.this.averageSimilarity/4;
						int  m = rand3.nextInt((int)Math.floor(AkkaSystem.this.averageCommonAnnotations));
						double t = rand4. nextInt(4)*AkkaSystem.this.averageSimilarity/4;
						char c = (char) (rand5.nextInt(3)+2);
						
							routees.get(i).increaseECWithFunctionalParameters((int)Math.floor(n/2), (int)Math.floor(m/2), s, t, '3');
						
						if(Math.random() < 0.4)
							routees.get(i).increaseFunctionalParametersWithPower((int)Math.floor(n/2), s, m*5, c, false, '3');
						
						if(Math.random() < 0.4)
							routees.get(i).increaseECByAddingPair((int)Math.floor(n/2),s, '3');
					}
					if(routees.get(i).getAlignmentNo()==3) {
						Random rand1 = new Random();
						Random rand2 = new Random();
						int  n = rand1.nextInt((int)Math.floor(AkkaSystem.this.averageCommonAnnotations));
						double s =  rand2.nextInt(4)*AkkaSystem.this.averageSimilarity/4;
						routees.get(i).increaseGOCAndBitScore((int)Math.floor(n/2), '3');
						
						routees.get(i).increaseBitScoreWithTopMappings(200, '3');
						
						if(Math.random() < 0.4)
							routees.get(i).increaseECByAddingPair((int)Math.floor(n/2), s, '3');
					}
					
					if(routees.get(i).getAlignmentNo()==4) {
						Random rand1 = new Random();
						Random rand2 = new Random();
						Random rand3 = new Random();
						Random rand4 = new Random();
						Random rand5 = new Random();
						int  n = rand1.nextInt((int)Math.floor(AkkaSystem.this.averageCommonAnnotations));
						int m = rand2.nextInt(AkkaSystem.this.computeOrderOfClusterForGivenAverageFactor(AkkaSystem.this.csLabelPropagationGO,1.0));
						int o = rand5.nextInt(AkkaSystem.this.computeOrderOfClusterForGivenAverageFactor(AkkaSystem.this.csLabelPropagationBitScore,1.0));
						char p = (char) (rand3.nextInt(3)+2);
						double s = rand4.nextInt(4)*AkkaSystem.this.averageSimilarity/4;
						boolean addPair = Math.random() < 0.5;
						boolean edges = Math.random() < 0.5;
						boolean tossBSGOC = Math.random() < 0.5; // BS daha ağırlıklı olmalı. GOC için aşağıdaki if leri tekrarla
						if(tossBSGOC) {
							if(edges)
								routees.get(i).alignClusterEdges((int)Math.floor(n/2), "labelpropagation", AkkaSystem.this.csLabelPropagationBitScore.get(o).clusterIDOfOrganism1, AkkaSystem.this.csLabelPropagationBitScore.get(o).clusterIDOfOrganism2, addPair, '3');
							else
								routees.get(i).alignClusters((int)Math.floor(n/2), s,"labelpropagation", AkkaSystem.this.csLabelPropagationBitScore.get(o).clusterIDOfOrganism1, AkkaSystem.this.csLabelPropagationBitScore.get(o).clusterIDOfOrganism2, '3');
						} else {
							if(edges)
								routees.get(i).alignClusterEdges((int)Math.floor(n/2), "labelpropagation", AkkaSystem.this.csLabelPropagationGO.get(m).clusterIDOfOrganism1, AkkaSystem.this.csLabelPropagationGO.get(m).clusterIDOfOrganism2, addPair, '3');
							else
								routees.get(i).alignClusters((int)Math.floor(n/2), s,"labelpropagation", AkkaSystem.this.csLabelPropagationGO.get(m).clusterIDOfOrganism1, AkkaSystem.this.csLabelPropagationGO.get(m).clusterIDOfOrganism2, '3');
						}
						
						if(Math.random() < 0.5)
							   routees.get(i).increaseBitScoreWithTopMappings(200, '3');
						
						if(Math.random() < 0.4)
							routees.get(i).increaseFunctionalParametersWithPower((int)Math.floor(n/2), s, (m+1)*5, p,false, '3');
						
						if(Math.random() < 0.5)
						routees.get(i).increaseECByAddingPair((int)Math.floor(n/2), s, '3');
						
						
					}
					if(routees.get(i).getAlignmentNo()==5) {
						Random rand1 = new Random();
						Random rand2 = new Random();
						Random rand3 = new Random();
						Random rand4 = new Random();
						Random rand5 = new Random();
						int  n = rand1.nextInt((int)Math.floor(AkkaSystem.this.averageCommonAnnotations));
						int m = rand2.nextInt(AkkaSystem.this.computeOrderOfClusterForGivenAverageFactor(AkkaSystem.this.csLouvainGO,1.0));
						int o = rand5.nextInt(AkkaSystem.this.computeOrderOfClusterForGivenAverageFactor(AkkaSystem.this.csLouvainBitScore,1.0));	
						char p = (char) (rand3.nextInt(3)+2);
						double s = rand4.nextInt(4)*AkkaSystem.this.averageSimilarity/4;
						boolean addPair = Math.random() < 0.5;
						boolean edges = Math.random() < 0.5;
						boolean tossBSGOC = Math.random() < 0.5;
						if(tossBSGOC) {
							if(edges)
								routees.get(i).alignClusterEdges((int)Math.floor(n/2), "louvain", AkkaSystem.this.csLouvainBitScore.get(o).clusterIDOfOrganism1, AkkaSystem.this.csLouvainBitScore.get(o).clusterIDOfOrganism2, addPair, '3');
							else
								routees.get(i).alignClusters((int)Math.floor(n/2), s,"louvain", AkkaSystem.this.csLouvainBitScore.get(o).clusterIDOfOrganism1, AkkaSystem.this.csLouvainBitScore.get(o).clusterIDOfOrganism2, '3');
						} else {
							if(edges)
								routees.get(i).alignClusterEdges((int)Math.floor(n/2), "louvain", AkkaSystem.this.csLouvainGO.get(m).clusterIDOfOrganism1, AkkaSystem.this.csLouvainGO.get(m).clusterIDOfOrganism2, addPair, '3');
							else
								routees.get(i).alignClusters((int)Math.floor(n/2), s,"louvain", AkkaSystem.this.csLouvainGO.get(m).clusterIDOfOrganism1, AkkaSystem.this.csLouvainGO.get(m).clusterIDOfOrganism2, '3');
						}
						
						if(Math.random() < 0.5)
							   routees.get(i).increaseBitScoreWithTopMappings(200, '3');
						
						if(Math.random() < 0.4)
							routees.get(i).increaseFunctionalParametersWithPower((int)Math.floor(n/2), s, (m+1)*5, p,false, '3');
						
						if(Math.random() < 0.5)
							routees.get(i).increaseECByAddingPair((int)Math.floor(n/2), s, '3');
							
					}
					
					if(routees.get(i).getAlignmentNo()==6)
					{
						Random rand1 = new Random();
						Random rand2 = new Random();
						Random rand3 = new Random();
						
						int  n = rand1.nextInt((int)Math.floor(AkkaSystem.this.averageCommonAnnotations/2));
						double s =  rand2.nextInt(4)*AkkaSystem.this.averageSimilarity/4;
						char p = (char) (rand3.nextInt(3)+2);

						if(Math.random() < 0.6)
						routees.get(i).alignAlternativeCentralNodesFromTop(n, s+1, 0.3, 1000, "pagerank", '3');
						
						if(Math.random() < 0.4)
						routees.get(i).alignCentralPowerNodesFromTop(n, s+1, p, 0.3, 1000, '3');
						
						if(Math.random() < 0.5)
							routees.get(i).increaseECByAddingPair(n, s, '3');
						else
							routees.get(i).increaseECByAddingPair((int)Math.floor(n/2), Math.floor(s/2), '3');
					}
					
					if(routees.get(i).getAlignmentNo()==7) {
						Random rand1 = new Random();
						Random rand2 = new Random();
						int  n = rand1.nextInt((int)Math.floor(AkkaSystem.this.averageCommonAnnotations));
						double s =  rand2.nextInt(4)*AkkaSystem.this.averageSimilarity/4;
						char p = (char) (rand2.nextInt(3)+2);
						routees.get(i).increaseBitScoreAndGOC(n, '3');
						
						// Ekledikten sonra sonuç görmedim.
						if(Math.random() < 0.5)
							routees.get(i).increaseFunctionalParametersWithPower((int)Math.floor(n/2), s ,n*5, p,false,'3');
						if(Math.random() < 0.5)
						   routees.get(i).increaseBitScoreWithTopMappings(200, '3');
						
						if(Math.random() < 0.4)
							routees.get(i).increaseECByAddingPair((int)Math.floor(n/2), s, '3');
						
					}
					
					if(routees.get(i).getAlignmentNo()==8)
					{
						Random rand1 = new Random();
						Random rand2 = new Random();
						Random rand3 = new Random();
						
						int  n = rand1.nextInt((int)Math.floor(AkkaSystem.this.averageCommonAnnotations/2));
						double s =  rand2.nextInt(4)*AkkaSystem.this.averageSimilarity/4;
						char p = (char) (rand3.nextInt(3)+2);

						if(Math.random() < 0.6)
						routees.get(i).alignAlternativeCentralNodesFromTop(n, s+1, 0.3, 1000, "betweenness", '3');
						
						if(Math.random() < 0.4)
						routees.get(i).alignCentralPowerNodesFromTop(n, s+1, p, 0.3, 1000, '3');
						
						if(Math.random() < 0.5)
							routees.get(i).increaseECByAddingPair(n, s, '3');
						else
							routees.get(i).increaseECByAddingPair((int)Math.floor(n/2), Math.floor(s/2), '3');
					}
					
					if(routees.get(i).getAlignmentNo()==9)
					{
						Random rand1 = new Random();
						Random rand2 = new Random();
						Random rand3 = new Random();
						
						int  n = rand1.nextInt((int)Math.floor(AkkaSystem.this.averageCommonAnnotations/2));
						double s =  rand2.nextInt(4)*AkkaSystem.this.averageSimilarity/4;
						char p = (char) (rand3.nextInt(3)+2);

						if(Math.random() < 0.6)
						routees.get(i).alignAlternativeCentralNodesFromTop(n, s+1, 0.3, 1000, "harmonic", '3');
						
						if(Math.random() < 0.4)
						routees.get(i).alignCentralPowerNodesFromTop(n, s+1, p, 0.3, 1000, '3');
						
						if(Math.random() < 0.5)
							routees.get(i).increaseECByAddingPair(n, s, '3');
						else
							routees.get(i).increaseECByAddingPair((int)Math.floor(n/2), Math.floor(s/2), '3');
					}
					
					if(routees.get(i).getAlignmentNo()==10)
					{
						Random rand1 = new Random();
						Random rand2 = new Random();
						Random rand3 = new Random();
						
						int  n = rand1.nextInt((int)Math.floor(AkkaSystem.this.averageCommonAnnotations/2));
						double s =  rand2.nextInt(4)*AkkaSystem.this.averageSimilarity/4;
						char p = (char) (rand3.nextInt(3)+2);

						if(Math.random() < 0.6)
						routees.get(i).alignAlternativeCentralNodesFromTop(n, s+1, 0.3, 1000, "closeness", '3');
						
						if(Math.random() < 0.4)
						routees.get(i).alignCentralPowerNodesFromTop(n, s+1, p, 0.3, 1000, '3');
						
						if(Math.random() < 0.5)
							routees.get(i).increaseECByAddingPair(n, s, '3');
						else
							routees.get(i).increaseECByAddingPair((int)Math.floor(n/2), Math.floor(s/2), '3');
					}
					
//					if(Math.random() < 0.1)
//						if(Math.random() < 0.5)
							
//						else
//							routees.get(i).removeWeakerOfManyToManyAlignments();
							
							if(Math.random() < 0.5)
								routees.get(i).removeBadMappingsRandomly(1, 1, true,0);
							else
								routees.get(i).removeLatterOfManyToManyAlignments();
							
//							if(routees.get(i).getNoofCyclesAlignmentUnchanged()>25){
//								// durağanlık durumunun neleri kapsaması gerektiği belirlenecek. Ona göre noofCyclesın artırıldığı ya da azaltıldığı yerler gözden geçirilecek.
//								// daha sonra durağanlık durumunda yapılacak ekstra silme işlemi ya da ekleme işlemine karar verilecek
//								System.err.println("Opening Some Search Space!!!!!");
//								double prob = Math.random();
//								
//								if(prob < 0.33)
//									routees.get(i).removeBadMappingsRandomly(0, 50, true,100);
//								else if (prob < 0.67)
//									routees.get(i).removeBadMappingsRandomly(2, 0, true,100);
//								else
//									routees.get(i).removeBadMappingsRandomly(0, 0, false,50);
//								
//								routees.get(i).setNoofCyclesAlignmentUnchanged(0);
//							}	
				}
		}
		}, AkkaSystem.system2.dispatcher());
	//Scheduler.schedule();
	
	return c;
}

public Future<Boolean> chainMappings(Future<Boolean> f, Aligner a, int minCommonAnnotations, double sim) {
		System.out.println(minCommonAnnotations+" - "+sim);
	Future<Boolean> chain = f.andThen(new OnComplete<Boolean>() {
    	public void onComplete(Throwable failure, Boolean success) {
    		if (success && failure==null) {
//    			System.out.println("Chain Trial 1");
    			a.increaseECByAddingPair(minCommonAnnotations, sim, '3');
    		} else
    			if (failure!=null)
    			System.out.println("Unchain My Heart! "+failure.getMessage());
    		}
    		}, AkkaSystem.system2.dispatcher()).recover(new Recover<Boolean>() {
    	public Boolean recover(Throwable problem) throws Throwable {
    		if (problem instanceof org.neo4j.driver.v1.exceptions.TransientException || problem instanceof Exception || problem !=null)
    		{
    			System.out.println("Chain Trial 2");
    			a.increaseECByAddingPair(minCommonAnnotations, sim, '3');
    			return true;
    		}
    			return false;
    		}
    		}, AkkaSystem.system2.dispatcher());
	return chain;
}

public void descendParameterValuesOfChain(Future<Boolean> f, Aligner a, int minCommonAnnotations, double sim, int noofSteps, boolean gocOrBS) {
	Future<Boolean> temp = f;
	int min = minCommonAnnotations;
	double simbs = sim;
	double stepSim = (sim - AkkaSystem.this.minSimilarity)/noofSteps;
	int stepAnnotation = (int)(Math.ceil(minCommonAnnotations - AkkaSystem.this.minCommonAnnotations)/noofSteps);
	if (stepAnnotation < 1) stepAnnotation = 1;
	if (stepSim < 30) stepSim = 30;
	if(!gocOrBS)
	while(simbs>=minSimilarity) {
		min = minCommonAnnotations;
		while (min>=AkkaSystem.this.minCommonAnnotations) {
			if(simbs<AkkaSystem.this.minSimilarity)
				simbs = 0;
			if(min<AkkaSystem.this.minCommonAnnotations)
				min = 0;
			temp = chainMappings(temp,a,min,simbs);
			System.out.println("added: "+min+" - "+simbs);
			min-=stepAnnotation;
		}
		simbs = simbs - stepSim;
	}
	else {
		while(min>=AkkaSystem.this.minCommonAnnotations) {
			simbs = sim;
			while (simbs>=minSimilarity) {
				if(simbs<AkkaSystem.this.minSimilarity)
					simbs = 0;
				if(min<AkkaSystem.this.minCommonAnnotations)
					min = 0;
				temp = chainMappings(temp,a,min,simbs);
				System.out.println("added: "+min+" - "+simbs);
				simbs = simbs-stepSim;
			}
			min-=stepAnnotation;
		}
	}
	
    try {
		Await.result(temp, timeout2.duration());
		
	} catch (Exception e1) {
		System.out.println("Futurelar yalan oldu::: "+e1.getMessage());;
	}
	
}
// initializes all previously recorded alignments in a given folder with the given file extension
public int initializePreviousAlignmentsFromFolder(int firstAlignerNo, String path, String extension) {
	int count = 0;
	try (Stream<Path> walk = Files.walk(Paths.get(path));
			FileWriter fw = new FileWriter("statistics.csv", true);
		    BufferedWriter bw = new BufferedWriter(fw);
		    PrintWriter out = new PrintWriter(bw)) {
		List<String> result = walk.map(x -> x.toString())
				.filter(f -> f.endsWith("."+extension)).collect(Collectors.toList());
		result.forEach(System.out::println);
		out.println(path);
		out.println("FileName,AlgNo,EC,ICS,S3,LCCS,GOC,BS,Size");
		for (String string : result) {
			Aligner a = new AlignerImpl(this,firstAlignerNo++);
			a.addAlignment(string);
			
			out.println(string.substring(path.length()+1)+","+a.getAlignmentNo()+","+a.getBenchmarkScores().getEC()+","+a.getBenchmarkScores().getICS()+","+a.getBenchmarkScores().getS3()+","+a.getBenchmarkScores().getLCCS()+","+a.getBenchmarkScores().getGOC()+","+a.getBenchmarkScores().getBitScore()+","+a.getBenchmarkScores().getSize());
			count++;
		}
		count++;
		count++;
		out.println("Files,Average,=AVERAGE(C3:C"+count+"),=AVERAGE(D3:D"+count+"),=AVERAGE(E3:E"+count+"),=AVERAGE(F3:F"+count+"),=AVERAGE(G3:G"+count+"),=AVERAGE(H3:H"+count+"),=AVERAGE(I3:I"+count+")");
		out.println("Files,Max,=MAX(C3:C"+count+"),=MAX(D3:D"+count+"),=MAX(E3:E"+count+"),=MAX(F3:F"+count+"),=MAX(G3:G"+count+"),=MAX(H3:H"+count+"),=MAX(I3:I"+count+")");
		out.println("Files,Min,=MIN(C3:C"+count+"),=MIN(D3:D"+count+"),=MIN(E3:E"+count+"),=MIN(F3:F"+count+"),=MIN(G3:G"+count+"),=MIN(H3:H"+count+"),=MIN(I3:I"+count+")");

	} catch (IOException e) {
		e.printStackTrace();
	}
	return count;
}

public static void removeLogFiles() {
	
	for (int i =1;i<11;i++) {
	    
	    File file2 = new File("add"+i+".txt");
	    if(file2.delete()){
	        System.out.println("add"+i+".txt File deleted from project root directory");
	    }else System.err.println("File add"+i+".txt doesn't exists in project root directory");
	    
	    File file6 = new File("calculateGlobalBenchmarks"+i+".txt");
	    if(file6.delete()){
	        System.out.println("calculateGlobalBenchmarks"+i+".txt File deleted from project root directory");
	    }else System.err.println("calculateGlobalBenchmarks"+i+".txt doesn't exists in project root directory");
		
	}
		
	}

public void printBenchmarkStatistics(String[] aligners,String label,int populationSize) {
	if(label!=null) {
		aligners =new String[populationSize];
		for (int i = 0;i<populationSize;i++)
			aligners[i] = label+(i+1)+".aln";
	}
	
		try(FileWriter fw = new FileWriter("statistics.csv", true);
			    BufferedWriter bw = new BufferedWriter(fw);
			    PrintWriter out = new PrintWriter(bw))
			{
			Aligner a;
			out.println("AlgNo,EC,ICS,S3,GOC,BS,Size");
			for (int i = 0;i<populationSize;i++) {
				a = new AlignerImpl(this,101+i);
				a.addAlignment(aligners[i]);
				out.println(a.getAlignmentNo()+","+a.getBenchmarkScores().getEC()+","+a.getBenchmarkScores().getICS()+","+a.getBenchmarkScores().getS3()+","+a.getBenchmarkScores().getGOC()+","+a.getBenchmarkScores().getBitScore()+","+a.getBenchmarkScores().getSize());
				a.removeAlignment();
			}
			out.println("Average,=AVERAGE(B2:B11),=AVERAGE(C2:C11),=AVERAGE(D2:D11),=AVERAGE(E2:E11),=AVERAGE(F2:F11),=AVERAGE(G2:G11)");
			out.println("Max,=MAX(B2:B11),=MAX(C2:C11),=MAX(D2:D11),=MAX(E2:E11),=MAX(F2:F11),=MAX(G2:G11)");
			out.println("Min,=MIN(B2:B11),=MIN(C2:C11),=MIN(D2:D11),=MIN(E2:E11),=MIN(F2:F11),=MIN(G2:G11)");
			} catch (IOException e) {
			    //exception handling left as an exercise for the reader
			}
}

	public void writeAlignments(String label,int populationSize) {
	for(int i = 1;i<populationSize+1;i++) {
		Aligner a = new AlignerImpl(this,i);
		a.writeAlignmentToDisk(label+i+".aln");
	}
}
	/*
	 * 
	 * args[0] -> Nodes of the organism with bigger number of nodes
	 * args[1] -> Nodes of the organism with smaller number of nodes
	 * args[2] -> Bitscore sequence similarity scores of the nodes of the bigger organism and the smaller organism
	 * args[3] -> Interactions of the organism with bigger number of nodes
	 * args[4] -> Interactions of the organism with smaller number of nodes
	 * args[5] -> Gene Ontology annotations  of the organism with bigger number of nodes
	 * args[6] -> Gene Ontology annotations  of the organism with smaller number of nodes
	 * args[7] -> Execution Mode
	 * args[8] -> Database address in the home directory of the current user
	 * args[9] -> String Label for the alignments to be saved/markedqueries to be loaded back from file.
	 * args[10] -> Setting the argument as "skipchains" deactivates the descendParameterValuesOfChain section of the alignment initializations when args[7] = 1.
	 * args[10] -> Tolerance value for the number of unexisting mappings when args[7] = 5.
	 * args[11] -> Setting the argument as "greedy" activates the greedy section of the alignment initializations.
	 * 
	 * args[7] = 1 -> All nodes and relationships are recreated. The alignment process is executed afterwards.
	 * args[7] = 11 -> Starts from computing community detection and centrality algorithms.
	 * args[7] = 2 -> All previous alignments are deleted. The alignment process is executed afterwards.
	 * args[7] = 3 -> The markedqueries of the previous alignment process is loaded from db into the application and the process is continued afterwards.
	 * args[7] = 33 -> The markedqueries of the previous alignment process is loaded from file to db and consecutively from db into the application and the process is continued afterwards.
	 * args[7] = 4 -> The alignment is recorded into files.
	 * args[7] = 5 -> Random search is executed for all mature alignments in the database.
	 * args[7] = 6 ->
	 * 
	 * */
	public static void main(String[] args) {
		databaseAddress = args[8];	
		final AkkaSystem as = new AkkaSystem(1,args[8],100,20);
		if(args[7].equals("1"))
		{
		as.deleteAllNodesRelationships();
		as.createGraph(args[0], args[1], args[2], args[3], args[4], args[5], args[6]);	
		as.computePowers();

		}
		
		if(args[7].equals("1")||args[7].equals("11")){
			as.removeAllAlignments();
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
		
		if (args[7].equals("5")) {
//			int tolerance = 0;
//			try {
//				if(Integer.parseInt(args[10])>0)
//				tolerance = Integer.parseInt(args[10]);
//			} catch (NumberFormatException e) {
//				// TODO Auto-generated catch block
//				System.err.println(e.getMessage());
//			} catch (ArrayIndexOutOfBoundsException aioobe) {
//				// TODO Auto-generated catch block
//				System.err.println(aioobe.getMessage()+" - Tolerance number is not entered");
//			}
//			for(int i =1;i<11;i++) {
//				Aligner a = new AlignerImpl(as, i);
//				
//				while(a.getBenchmarkScores().getSize() !=as.sizeOfSecondNetwork-tolerance) {
//				a.addMeaninglessMapping(100, '3');
//				a.increaseBitScoreWithTopMappings(20, '3');
//				a.increaseECByAddingPair(0, 0, '3');
//				a.removeBadMappingsToReduceInduction1(true, 0, 0, 0);
//				a.removeBadMappings(1, 1, true, 100);
//			} 	
//			}
			as.removeAllAlignments();
			int populationSize = as.initializePreviousAlignmentsFromFolder(1, "spinal/cedmversionii", "txt");

//			
//			Random rand4 = new Random();
//			double s = rand4.nextInt(4)*as.averageSimilarity/4;
//			System.out.println(s);
//			System.out.println(as.averageSimilarity/4);
		
//			Aligner a = new AlignerImpl(as, 91);
//			a.removeAlignment();
//			a.createAlignment("sana.align");
//			as.calculateGlobalBenchmarks(a);
//			a.addMeaninglessMapping(5, '3');
		}
		
//		Aligner a = new AlignerImpl(as, 1);
//		a.removeAlignment();
//		a.createAlignment("sana.align");
//		as.calculateGlobalBenchmarks(a);
//		a.addMeaninglessMapping(5, '3');
//		a.createAlignment("/home/giray/Dropbox/ProteinAlignment/alignments/dmsc/12022019/34/DMSC34Save2.aln");
//		for (int i = 0; i < 20; i++) {
//			a.addMeaninglessMapping(100, '3');
//			a.increaseBitScoreWithTopMappings(20, '3');
//			a.increaseECByAddingPair(0, 0, '3');
//			a.removeBadMappingsToReduceInduction1(0, 0, true, 0, 0, 0);
//			a.removeBadMappings(1, 1, true, 100);
//
//		} 
	    
//	    ActorRef router = as.system2.actorOf(new RoundRobinGroup(routeePaths).props(), "router");
	 //   router.tell("1 deneme",as.typed.getActorRefFor(secondAligner));
//	    as.typed.getActorRefFor(secondAligner).tell("1 deneme", as.typed.getActorRefFor(firstAligner));
		
		
		if (args[7].equals("2")) {
			removeLogFiles();
			as.removeAllMarks();
			as.removeAllQueries();
			as.removeAllAlignments();
		}
		
		if(args[7].equals("3"))
			as.loadOldMarkedQueriesFromDBToApplication();
		
		if(args[7].equals("33")) {
			
			for (int i=1;i<11;i++) {
				Aligner a = new AlignerImpl(as, i);
				a.addAlignment(args[9]+"Save"+i+".aln");
			}
			
			as.loadOldMarkedQueriesFromFileToDB(args[9]+".txt");
			as.loadOldMarkedQueriesFromDBToApplication();
		}
		
		
		if (args[7].equals("1") || args[7].equals("11")|| args[7].equals("2") ||args[7].equals("3")||args[7].equals("33")) {
			
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
				
				if(!args[10].equals("skipchains")) {				
					as.descendParameterValuesOfChain(f, firstAligner, (int)Math.floor(as.averageCommonAnnotations), as.averageSimilarity, (int)Math.ceil(as.averageSimilarity/as.minSimilarity),true);
					as.descendParameterValuesOfChain(f3, sixthAligner, (int)Math.floor(as.averageCommonAnnotations), as.averageSimilarity, (int)Math.ceil(as.averageSimilarity/as.minSimilarity),true);
					as.descendParameterValuesOfChain(f5, eighthAligner, (int)Math.floor(as.averageCommonAnnotations), as.averageSimilarity, (int)Math.ceil(as.averageSimilarity/as.minSimilarity),true);
					as.descendParameterValuesOfChain(f7, ninthAligner, (int)Math.floor(as.averageCommonAnnotations), as.averageSimilarity, (int)Math.ceil(as.averageSimilarity/as.minSimilarity),true);
					as.descendParameterValuesOfChain(f9, tenthAligner, (int)Math.floor(as.averageCommonAnnotations), as.averageSimilarity, (int)Math.ceil(as.averageSimilarity/as.minSimilarity),true);
				}
				
				if(args[11].equals("greedy")) {
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
							//  		if (problem instanceof Exception)
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
							//   		else
							//  		throw problem;
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
							//  		if (problem instanceof Exception)
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
							//   		else
							//  		throw problem;
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
							//   		else
							//  		throw problem;
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
							//  		if (problem instanceof Exception)
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
							//   		else
							//  		throw problem;
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
							//   		else
							//  		throw problem;
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
						;
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
				//			as.markPowerAlignments(150, 150);
				//			as.removeManyToManyAlignments(500,500);
				as.retryPreviouslyMarkedQueries(500, 500);
				//			as.retryStrongerPreviouslyMarkedQueries(1000, 1000);
				//			as.writeBestAlignmentsInTime(2000,2000);
			} catch (Exception e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
				as.sendBestBenchmarkScoresInTime(300, 200);
				as.markBestSubGraphsInTime(300, 300);
				as.addRandomMapping(200, 200);
				//			as.markPowerAlignments(150, 150);
				//			as.removeManyToManyAlignments(500,500);
				as.retryPreviouslyMarkedQueries(500, 500);
				//			as.retryStrongerPreviouslyMarkedQueries(1000, 1000);
				//			as.writeBestAlignmentsInTime(2000,2000);
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
			
			if (args[7].equals("4")) {
				as.writeAlignments(args[9]+"Save", 10);
				as.printBenchmarkStatistics(null, args[9]+"Save", 10);
				as.saveOldMarkedQueriesToFile(args[9]+".txt");
			}
		
	}
	
	@SuppressWarnings("unused")
	private static Future<Boolean> future(Callable<Boolean> callable, ExecutionContextExecutor dispatcher) {
		// TODO Auto-generated method stub
		try {
			return Futures.future(callable,dispatcher);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			System.err.println("fevzi");
			return Futures.successful(true);
		}
	}
	
	public final static class PrintResult<T> extends OnSuccess<T> {
		@Override public final void onSuccess(T t) {		
		System.out.println("Futurelar gerçek oldu. Gönderen: "+((SubGraph) t).senderNo+" Çeşit: "
		+((SubGraph) t).type+" including "+((SubGraph) t).aligns.size()+" mappings"/*+"\n"+"İçerik: "+t*/);
		}
		}

}

class ClusterSimilarity {
	long clusterIDOfOrganism1;
	long clusterIDOfOrganism2;
	double similarity;
	
	ClusterSimilarity(long cidoo1,long cidoo2, double sim){
		clusterIDOfOrganism1 = cidoo1;
		clusterIDOfOrganism2 = cidoo2;
		similarity = sim;
	}
	
}
