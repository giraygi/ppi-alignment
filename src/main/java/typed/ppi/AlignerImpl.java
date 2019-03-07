package typed.ppi;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.exceptions.NoSuchRecordException;
import org.neo4j.driver.v1.summary.ResultSummary;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.driver.v1.types.Relationship;
import org.neo4j.helpers.TransactionTemplate;

import typed.ppi.BenchmarkScores;
import typed.ppi.SubGraph;
import typed.ppi.AkkaSystem.PrintResult;
import akka.actor.ActorRef;
import akka.actor.TypedActor;
import akka.actor.TypedActor.Receiver;
import akka.dispatch.Futures;
import akka.dispatch.OnFailure;
import scala.concurrent.ExecutionContextExecutor;
import scala.concurrent.Future;

public class AlignerImpl implements Aligner, Receiver {
	
	/**
	 * @param args
	 */
	
	FileReader fr;
	BufferedReader br;
	FileWriter fw;
	BufferedWriter bw;
	Session session;
	Session innerSession;
	Session futureSession;
	int maxCommonAnnotations = 0;
	double maxSimilarity = 0.0;
	int alignmentNo;
	SubGraph unalignedNodes;
	SubGraph alignedEdges;
	SubGraph conservedStructures;
	SubGraph kGOT;
	SubGraph xBitScore;
	SubGraph clusterEdges;
	SubGraph powerNodes;
	BenchmarkScores bs;
	AkkaSystem as;
	String mesaj = "keriz avcısı";
	List<BenchmarkScores> markedQueries = new ArrayList<BenchmarkScores>(); //???
	static int received = 0;
	int noofCyclesAlignmentUnchanged = 0;

	public AlignerImpl(AkkaSystem as,int alignmentNo) {
		this.as = as;
		session = AkkaSystem.driver.session();
		innerSession = AkkaSystem.driver.session();
		futureSession = AkkaSystem.driver.session();
		maxCommonAnnotations = as.maxCommonAnnotations;
		maxSimilarity = as.maxSimilarity;
		this.alignmentNo = alignmentNo;
		bs = new BenchmarkScores(alignmentNo);
	}

	public AlignerImpl(String string) {
		// TODO Auto-generated constructor stub
	}

	/**
	 * Adresi alignment katarında bulunan bir dosyadaki hizalamalara göre numarası ile birlikte bir hizalama yaratmak
	 * Bir hizalama numarasina has birebir iliski sa�lamak icin hizalamanin numarasi ve hizalad��� d���m isimlerinin birle�iminden olu�an ��l� bir unique constraint tan�mlayabiliriz. 
	 **/
	public void createAlignment(String alignment) {
		System.out.println("Creating Alignment: "+alignment);
		TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
		int added = template.with(AkkaSystem.graphDb).execute( transaction -> {
			Session create = AkkaSystem.driver.session();
			int count = 0;
			ResultSummary rs;
		try ( org.neo4j.driver.v1.Transaction tx = create.beginTransaction()  )
		{
			fr = new FileReader(alignment);
			br =  new BufferedReader(fr); 
			String line = null;
			String[] proteinPair;
			while((line = br.readLine())!=null)
			{
				proteinPair = line.split(" ");
				rs = tx.run("match (n:Organism2 {proteinName: '"+proteinPair[0]+"'}), (m:Organism1 {proteinName: '"+proteinPair[1]+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+proteinPair[0]+"*"+alignmentNo+"*"+proteinPair[1]+"',markedQuery:[]}]->(m)").consume();
				count+=rs.counters().relationshipsCreated();
			}	
			tx.success(); tx.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {create.close();}
		this.bs = as.calculateGlobalBenchmarks(this);
		return count;
	} );
		System.out.println(added+" mappings were added with createAlignment(String alignment)");
	}
	// addAlignment(SubGraph alignment) varken gerek olmayabilir.
	public void createAlignment(SubGraph alignment){
		
	}
	
	public void copyAlignment(int otherAlignmentNo) {
		System.out.println("GODZİLLA");	
		TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
		boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
			Session copy = AkkaSystem.driver.session();
			try ( org.neo4j.driver.v1.Transaction tx = copy.beginTransaction()  )
			{
			tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where r.alignmentNumber = '"+otherAlignmentNo+"' create (n)-[:ALIGNS {alignmentNumber: '"+this.alignmentNo+"', alignmentIndex: n.proteinName+'*"+this.alignmentNo+"*'+m.proteinName,markedQuery:[]}]->(m)");
			tx.success(); tx.close();
		} catch (Exception e){
			System.out.println("copyAlignment: " + e.getMessage());
		} finally {copy.close();}
			this.bs = as.calculateGlobalBenchmarks(this);
			return true;
		} );
		if(success)
			System.out.println("Aligner "+otherAlignmentNo+" is copied into "+this.alignmentNo);
	}
	
	public void createAlignment(long markedQuery,int minFrequency){	
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction()  )
		{
		tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where LENGTH(FILTER(x IN r.markedQuery WHERE x = '"+markedQuery+"')) >="+minFrequency+" create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: n.proteinName+'*"+alignmentNo+"*'+m.proteinName,markedQuery:[]}]->(m)");
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}
		this.bs = as.calculateGlobalBenchmarks(this);
	}
	
	/*
	 * Mevcut hizalamada eslesmeyen dugumlere dosyada bulunan bir hizalamadaki eslesmeleri ekleyen metot.
	 *  
	 * */
	
	public void addAlignment(String alignment) {
		System.out.println("Adding Alignment: "+alignment);
		
		TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
		int added = template.with(AkkaSystem.graphDb).execute( transaction -> {
			Session add = AkkaSystem.driver.session();
			int count = 0;
		try ( org.neo4j.driver.v1.Transaction tx = add.beginTransaction()  )
		{		
			markUnalignedNodes();
			fr = new FileReader(alignment);
			br =  new BufferedReader(fr); 
			String line = null;
			String[] proteinPair;
			while((line = br.readLine())!=null)
			{
				proteinPair = line.split(" ");		
				tx.run("match (n:Organism2 {proteinName: '"+proteinPair[0]+"'}), (m:Organism1 {proteinName: '"+proteinPair[1]+"'}) where ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+proteinPair[0]+"*"+alignmentNo+"*"+proteinPair[1]+"',markedQuery:[]}]->(m)");
				count++;
			}	
			tx.run("MATCH (n) SET n.marked = FILTER(x IN n.marked WHERE x <> '"+this.alignmentNo+"')");
			tx.success(); tx.close();
		} catch (Exception e){
			System.err.println( "Add Alignment int::: " + e.getMessage());
			if (Math.random() < 0.5)
				addAlignment(alignment);
		}	finally {add.close();}
		this.bs = as.calculateGlobalBenchmarks(this);
		return count;
	} );
		System.out.println(added+" mappings were added with addAlignment(String alignment)");
		this.bs = as.calculateGlobalBenchmarks(this);	
	}
	// eksik olabilir. uygulama içinde test edilmedi
public void addAlignment(SubGraph alignment){
	
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
	int added = template.with(AkkaSystem.graphDb).execute( transaction -> {
		Session aa = AkkaSystem.driver.session();
		System.out.println("SubGraph received from aligner "+alignment.senderNo+" is added to alignment no: "+this.alignmentNo);
		SubGraph diff  = findAlignedNodesRelationships().difference(alignment);
		int count = 0;
		ResultSummary rs;
		try ( org.neo4j.driver.v1.Transaction tx = aa.beginTransaction()  )
		{	
			
			Object[] as = diff.aligns.toArray();
			
			for (Object relationship : as) {
//				System.out.println(((Relationship) relationship).startNodeId()+" - "+((Relationship) relationship).endNodeId());
				rs = tx.run("match (n:Organism2),(m:Organism1) where id(n) = "+((Relationship) relationship).startNodeId()+" and id(m) = "+((Relationship) relationship).endNodeId()+" create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: n.proteinName+'*"+alignmentNo+"*'+m.proteinName,markedQuery:[]}]->(m)").consume();
				count+=rs.counters().relationshipsCreated();
			}
			
//			while(diff.aligns.iterator().hasNext()){
//			rel = diff.aligns.iterator().next();
//			System.out.println(rel.startNodeId()+" - "+rel.endNodeId());
//			tx.run("match (n:Organism2),(m:Organism1) where id(n) = "+rel.startNodeId()+" and id(m) = "+rel.endNodeId()+" create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: n.proteinName+'*"+alignmentNo+"*'+m.proteinName,markedQuery:[]}]->(m)");
//			count++;
//			}
		tx.success(); tx.close();
		} catch (Exception e){
			System.out.println("Add Alignment SubGraph::: " + e.getMessage());
			if (Math.random() < 0.5)
			addAlignment(alignment);
		} finally {
			aa.close();
			if(alignment.type.contains("Top PowerNode Pairs")) {
				Random rand = new Random();
				Random rand2 = new Random();
				int  n = rand.nextInt(3);
				int m = rand2.nextInt(3);
				this.increaseECByAddingPair(n, m*40, '3');
			}
				
		}
			
		/*
		 SubGraph unaligned = findUnalignedNodes();
		unaligned.nodes1.removeAll(alignment.nodes1);
		unaligned.nodes2.removeAll(alignment.nodes2);
		Relationship temp;
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction()  )
		{	
		while(alignment.aligns.iterator().hasNext()){
			temp = alignment.aligns.iterator().next();
			tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where id(n) = "+temp.startNodeId()+" and id(m) = "+temp.endNodeId()+" create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: n.proteinName+'*"+alignmentNo+"*'+m.proteinName,markedQuery:[]}]->(m)");	
			}
		tx.success(); tx.close();
		} catch (Exception e){
			e.printStackTrace();
		}
		this.bs = as.calculateGlobalBenchmarks(this); 
		 * */
		return count;
	} );
	
	
	try(FileWriter fw = new FileWriter("add"+this.alignmentNo+".txt", true);
		    BufferedWriter bw = new BufferedWriter(fw);
		    PrintWriter out = new PrintWriter(bw))
		{
			out.println("["+received+++" | SubGraph | "+ZonedDateTime.now()+"]: "+added+" mappings are added from SubGraph "+alignment.type+" of aligner "+alignment.senderNo+" to aligner "+this.alignmentNo);
		} catch (IOException e) {
		    //exception handling left as an exercise for the reader
		}
	
	System.out.println(added+" mappings are added from SubGraph "+alignment.type+" of aligner "+alignment.senderNo+" to aligner "+this.alignmentNo);
	if(added<=0)
		noofCyclesAlignmentUnchanged++;
	else
		noofCyclesAlignmentUnchanged = 0;
	this.bs = as.calculateGlobalBenchmarks(this);	
}

public void addAlignment(int alignmentNo) {
	
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
	int added = template.with(AkkaSystem.graphDb).execute( transaction -> {
		Session aa = AkkaSystem.driver.session();
		ResultSummary rs = null; 
	try ( org.neo4j.driver.v1.Transaction tx = aa.beginTransaction()  )
	{
	markUnalignedNodes();
	rs = tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where r.alignmentNumber = '"+alignmentNo+"' and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') create (n)-[:ALIGNS {alignmentNumber: '"+this.alignmentNo+"', alignmentIndex: n.proteinName+'*"+this.alignmentNo+"*'+m.proteinName,markedQuery:[]}]->(m)").consume();
	tx.run("MATCH (n) SET n.marked = FILTER(x IN n.marked WHERE x <> '"+this.alignmentNo+"')");
	tx.success(); tx.close();
} catch (Exception e){
	System.err.println( "Add Alignment int::: " + e.getMessage());
	if (Math.random() < 0.5)
		addAlignment(alignmentNo);
}finally {aa.close();}
	this.bs = as.calculateGlobalBenchmarks(this);
	return rs.counters().relationshipsCreated();
} );
	
	try(FileWriter fw = new FileWriter("add"+this.alignmentNo+".txt", true);
		    BufferedWriter bw = new BufferedWriter(fw);
		    PrintWriter out = new PrintWriter(bw))
		{
			out.println("["+received+++" | Pareto | "+ZonedDateTime.now()+"]: "+added+" mappings are added from aligner "+alignmentNo+" to aligner "+this.alignmentNo);
		} catch (IOException e) {
		    //exception handling left as an exercise for the reader
		}
	
	System.out.println(added+" mappings are added from aligner "+alignmentNo+" to aligner "+this.alignmentNo);
	if(added<=0)
		noofCyclesAlignmentUnchanged++;
	else
		noofCyclesAlignmentUnchanged = 0;
}

//uygulama içinde test edilmedi. markedQuery sırasına göre azalan sıralanması için order by LENGTH(FILTER(x IN r.markedQuery WHERE x = '"+markedQuery+"')) desc ifadesi eklendi.

public void addAlignment(long markedQuery, int minFrequency){
	
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
	int added = template.with(AkkaSystem.graphDb).execute( transaction -> {
		Session aa = AkkaSystem.driver.session();
		ResultSummary rs = null; 
	try ( org.neo4j.driver.v1.Transaction tx = aa.beginTransaction()  )
	{
	markUnalignedNodes();
	rs = tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where LENGTH(FILTER(x IN r.markedQuery WHERE x = '"+markedQuery+"')) >="+minFrequency+" and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') with n,r,m order by LENGTH(FILTER(x IN r.markedQuery WHERE x = '"+markedQuery+"')) desc create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: n.proteinName+'*"+alignmentNo+"*'+m.proteinName,markedQuery:[]}]->(m)").consume();
	tx.run("MATCH (n) SET n.marked = FILTER(x IN n.marked WHERE x <> '"+this.alignmentNo+"')");
	//unmarkAllNodes();
	tx.success(); tx.close();
} catch (Exception e){
	System.err.println("Add Alignment long::: " + e.getMessage());
	if (Math.random() < 0.5)
	addAlignment(markedQuery,minFrequency);
}finally {aa.close();}
	this.bs = as.calculateGlobalBenchmarks(this);
	return rs.counters().relationshipsCreated();
} );
	
	try(FileWriter fw = new FileWriter("add"+this.alignmentNo+".txt", true);
		    BufferedWriter bw = new BufferedWriter(fw);
		    PrintWriter out = new PrintWriter(bw))
		{
			out.println("["+received+++" | Long | "+ZonedDateTime.now()+"]: "+added+" mappings are added from markedQuery "+markedQuery+" to aligner "+this.alignmentNo);
		} catch (IOException e) {
		    //exception handling left as an exercise for the reader
		}
	
	System.out.println(added+" mappings are added from markedQuery "+markedQuery+" to aligner "+this.alignmentNo);
	if(added<=0)
		noofCyclesAlignmentUnchanged++;
	else
		noofCyclesAlignmentUnchanged = 0;
}

public Future<SubGraph> currentAlignment(ExecutionContextExecutor dispatcher) {
	
	SubGraph sg = new SubGraph(this);
	Session ca = AkkaSystem.driver.session();
	
	Future<SubGraph> f = future(new Callable<SubGraph>() {
		public SubGraph call() {
	
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
	SubGraph success = template.with(AkkaSystem.graphDb).execute( transaction -> {
		SubGraph sgca = sg;
		StatementResult result;
		try ( org.neo4j.driver.v1.Transaction tx = ca.beginTransaction() )
	      {
			result = tx.run("match (q:Organism2)-[a:ALIGNS {alignmentNumber: '"+alignmentNo+"'}]->(p:Organism1) "
					+ "return q,a,p");
			while(result.hasNext()){
				Record row = result.next();
				
				for ( Entry<String,Object> column : row.asMap().entrySet() ){
					if(column.getValue()!=null)
						switch (column.getKey()) {
						case "q":
							sgca.nodes2.add(row.get( column.getKey() ).asNode());
							break;
						case "a":
							sgca.aligns.add(row.get( column.getKey() ).asRelationship());
							break;
						case "p":
							sgca.nodes1.add(row.get( column.getKey() ).asNode());
							break;

						default:
							System.out.println("Unexpected column"+column.getKey());
							break;
						}
					}
				}
			sgca.type = "Subgraph of the current alignment";
			tx.success(); tx.close();
	      } catch (Exception e){
	    	  System.out.println("SubGraph With The Whole Alignment::: " + e.getMessage());
	    	  currentAlignment(dispatcher);
	      } finally {ca.close();}
		return sgca;
	} );
	
	return success;
		}
		}, dispatcher);
	return f;
}

public Future<SubGraph> subGraphWithAlignedEdges(ExecutionContextExecutor dispatcher){
// query for double aligned edges
//	match (y:Organism2)-[d:INTERACTS_2]-(x:Organism2)-[a:ALIGNS]->(n:Organism1)-[e:INTERACTS_1]-(m:Organism1)-[g:INTERACTS_1]-(o:Organism1)<-[c:ALIGNS]-(z:Organism2)-[f:INTERACTS_2]-(y)-[b:ALIGNS]->(m) where a.alignmentNumber = '1' and b.alignmentNumber = '1' and c.alignmentNumber = '1' return distinct x,d,y,f,z,a,b,c,n,e,m,g,o
	SubGraph s = new SubGraph(this);
	Session  sgwaes = AkkaSystem.driver.session();
	Future<SubGraph> f = future(new Callable<SubGraph>() {
		public SubGraph call() {
			
			TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
			SubGraph success = template.with(AkkaSystem.graphDb).execute( transaction -> {
				SubGraph sgwae = s;
				StatementResult result;
				try ( org.neo4j.driver.v1.Transaction tx = sgwaes.beginTransaction() )
			      {
					result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[t:ALIGNS]->(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)<-[s:ALIGNS]-(o) where s.alignmentNumber = '"+alignmentNo+"' and t.alignmentNumber = '"+alignmentNo+"' return (o),(p),(n),(m),(u),(t),(r),(s)");
					while(result.hasNext()){
						Record row = result.next();
						for ( Entry<String,Object> column : row.asMap().entrySet() ){
							if(column.getValue()!=null)
								switch (column.getKey()) {
							case "o":
								sgwae.nodes2.add((Node) row.get( column.getKey() ).asNode());
								break;
							case "u":
								sgwae.interactions2.add((Relationship) row.get( column.getKey() ).asRelationship());
								break;
							case "p":
								sgwae.nodes2.add((Node) row.get( column.getKey() ).asNode());
								break;
							case "t":
								sgwae.aligns.add((Relationship) row.get( column.getKey() ).asRelationship());
								break;
							case "n":
								sgwae.nodes1.add((Node) row.get( column.getKey() ).asNode());
								break;
							case "r":
								sgwae.interactions1.add((Relationship) row.get( column.getKey() ).asRelationship());
								break;
							case "m":
								sgwae.nodes1.add((Node) row.get( column.getKey() ).asNode());
								break;
							case "s":
								sgwae.aligns.add((Relationship) row.get( column.getKey() ).asRelationship());
								break;
							default:
								break;
							}			
//							System.out.printf( "%s = %s%n", column.getKey(), row.get( column.getKey() ) );
				         }
						
					}
					sgwae.type = "Subgraph with Aligned Edges";
					tx.success(); tx.close();

			      } catch (Exception e){
			    	  System.out.println("SubGraph With Aligned Edges::: " + e.getMessage());
			    	  subGraphWithAlignedEdges(dispatcher);
			      } finally {sgwaes.close();}
				AlignerImpl.this.alignedEdges = sgwae;
				return sgwae;
			} );
			
			return success;
		}
		}, dispatcher);
//	this.alignedEdges = sgwae;
	return f;
}

public Future<Long> markAlignedEdges(long markedQuery, ExecutionContextExecutor dispatcher){
	
	Future<Long> f = gelecek(new Callable<Long>() {
		public Long call() {
			TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
			tm.failure(new Throwable("Herkesin tuttuğu kendine"));
			TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
			boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
				ResultSummary rs = null;
				Session markAlignedSession = AkkaSystem.driver.session();
				try{
					rs = markAlignedSession.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[t:ALIGNS]->(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)<-[s:ALIGNS]-(o) where s.alignmentNumber = '"+alignmentNo+"' and t.alignmentNumber = '"+alignmentNo+"' "
							+ "set t.markedQuery = case when not ANY(x IN t.markedQuery WHERE x = '"+markedQuery+"') then t.markedQuery+'"+markedQuery+"' else t.markedQuery end, "
							+ "s.markedQuery = case when not ANY(x IN s.markedQuery WHERE x = '"+markedQuery+"') then s.markedQuery+'"+markedQuery+"' else s.markedQuery end, "
							+ "o.markedQuery = case when not ANY(x IN o.markedQuery WHERE x = '"+markedQuery+"') then o.markedQuery+'"+markedQuery+"' else o.markedQuery end, "
							+ "p.markedQuery = case when not ANY(x IN p.markedQuery WHERE x = '"+markedQuery+"') then p.markedQuery+'"+markedQuery+"' else p.markedQuery end, "
							+ "n.markedQuery = case when not ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') then n.markedQuery+'"+markedQuery+"' else n.markedQuery end, "
							+ "m.markedQuery = case when not ANY(x IN m.markedQuery WHERE x = '"+markedQuery+"') then m.markedQuery+'"+markedQuery+"' else m.markedQuery end, "
							+ "u.markedQuery = case when not ANY(x IN u.markedQuery WHERE x = '"+markedQuery+"') then u.markedQuery+'"+markedQuery+"' else u.markedQuery end, "
							+ "r.markedQuery = case when not ANY(x IN r.markedQuery WHERE x = '"+markedQuery+"') then r.markedQuery+'"+markedQuery+"' else r.markedQuery end").consume();
				} catch(Exception e){
					System.err.println("Mark Aligned Edges::: "+e.getMessage());
//					if(Math.random() < 0.5)
//					markAlignedEdges(markedQuery, dispatcher) ;
				} finally {markAlignedSession.close();}
				System.out.println(rs.counters().propertiesSet()+" properties were set on nodes and relationships for Aligned Edges");
				return true;
			} );
	if(success) 
		{
			markedQueries.add(as.calculateSubGraphBenchmarks((Aligner) AlignerImpl.this , String.valueOf(markedQuery)));
			return markedQuery;	
		}
	else return 0L;	
}
}, dispatcher);
return f;
	
}

public Future<SubGraph> subGraphWithDoubleAlignedEdges(ExecutionContextExecutor dispatcher){
	// query for double aligned edges
//		match (y:Organism2)-[d:INTERACTS_2]-(x:Organism2)-[a:ALIGNS]->(n:Organism1)-[e:INTERACTS_1]-(m:Organism1)-[g:INTERACTS_1]-(o:Organism1)<-[c:ALIGNS]-(z:Organism2)-[f:INTERACTS_2]-(y)-[b:ALIGNS]->(m) where a.alignmentNumber = '1' and b.alignmentNumber = '1' and c.alignmentNumber = '1' return distinct x,d,y,f,z,a,b,c,n,e,m,g,o
		SubGraph s = new SubGraph(this);
		Session  sgwaes = AkkaSystem.driver.session();
		Future<SubGraph> f = future(new Callable<SubGraph>() {
			public SubGraph call() {
				
				TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
				SubGraph success = template.with(AkkaSystem.graphDb).execute( transaction -> {
					SubGraph sgwae = s;
					StatementResult result;
					try ( org.neo4j.driver.v1.Transaction tx = sgwaes.beginTransaction() )
				      {
						result = tx.run( "match (m:Organism1)-[w:INTERACTS_1]-(l:Organism1)<-[v:ALIGNS]-(q:Organism2)-[x:INTERACTS_2]-(o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[t:ALIGNS]->(n:Organism1)-[r:INTERACTS_1]-(m)<-[s:ALIGNS]-(o) where s.alignmentNumber = '"+alignmentNo+"' and t.alignmentNumber = '"+alignmentNo+"' return (o),(p),(n),(m),(u),(t),(r),(s)");
						while(result.hasNext()){
							Record row = result.next();
							for ( Entry<String,Object> column : row.asMap().entrySet() ){
								if(column.getValue()!=null)
									switch (column.getKey()) {
								case "o":
									sgwae.nodes2.add((Node) row.get( column.getKey() ).asNode());
									break;
								case "u":
									sgwae.interactions2.add((Relationship) row.get( column.getKey() ).asRelationship());
									break;
								case "p":
									sgwae.nodes2.add((Node) row.get( column.getKey() ).asNode());
									break;
								case "t":
									sgwae.aligns.add((Relationship) row.get( column.getKey() ).asRelationship());
									break;
								case "n":
									sgwae.nodes1.add((Node) row.get( column.getKey() ).asNode());
									break;
								case "r":
									sgwae.interactions1.add((Relationship) row.get( column.getKey() ).asRelationship());
									break;
								case "m":
									sgwae.nodes1.add((Node) row.get( column.getKey() ).asNode());
									break;
								case "s":
									sgwae.aligns.add((Relationship) row.get( column.getKey() ).asRelationship());
									break;
								case "x":
									sgwae.interactions2.add((Relationship) row.get( column.getKey() ).asRelationship());
									break;
								case "q":
									sgwae.nodes2.add((Node) row.get( column.getKey() ).asNode());
									break;
								case "v":
									sgwae.aligns.add((Relationship) row.get( column.getKey() ).asRelationship());
									break;
								case "l":
									sgwae.nodes1.add((Node) row.get( column.getKey() ).asNode());
									break;
								case "w":
									sgwae.interactions1.add((Relationship) row.get( column.getKey() ).asRelationship());
									break;
								default:
									break;
								}			
//								System.out.printf( "%s = %s%n", column.getKey(), row.get( column.getKey() ) );
					         }
							
						}
						sgwae.type = "Subgraph with Double Aligned Edges";
						tx.success(); tx.close();

				      } catch (Exception e){
				    	  System.out.println("SubGraph With Double Aligned Edges::: " + e.getMessage());
				    	  subGraphWithAlignedEdges(dispatcher);
				      } finally {sgwaes.close();}
					AlignerImpl.this.alignedEdges = sgwae;
					return sgwae;
				} );
				
				return success;
			}
			}, dispatcher);
//		this.alignedEdges = sgwae;
		return f;
	}

	public Future<Long> markDoubleAlignedEdges(long markedQuery, ExecutionContextExecutor dispatcher){
		
		Future<Long> f = gelecek(new Callable<Long>() {
			public Long call() {
				TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
				tm.failure(new Throwable("Herkesin tuttuğu kendine"));
				TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
				boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
					ResultSummary rs = null;
					Session markAlignedSession = AkkaSystem.driver.session();
					try{															// 
						rs = markAlignedSession.run( "match (m:Organism1)-[w:INTERACTS_1]-(l:Organism1)<-[v:ALIGNS]-(q:Organism2)-[x:INTERACTS_2]-(o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[t:ALIGNS]->(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)<-[s:ALIGNS]-(o) where s.alignmentNumber = '"+alignmentNo+"' and t.alignmentNumber = '"+alignmentNo+"' "
								+ "set t.markedQuery = case when not ANY(x IN t.markedQuery WHERE x = '"+markedQuery+"') then t.markedQuery+'"+markedQuery+"' else t.markedQuery end, "
								+ "s.markedQuery = case when not ANY(x IN s.markedQuery WHERE x = '"+markedQuery+"') then s.markedQuery+'"+markedQuery+"' else s.markedQuery end, "
								+ "v.markedQuery = case when not ANY(x IN v.markedQuery WHERE x = '"+markedQuery+"') then v.markedQuery+'"+markedQuery+"' else v.markedQuery end, "
								+ "o.markedQuery = case when not ANY(x IN o.markedQuery WHERE x = '"+markedQuery+"') then o.markedQuery+'"+markedQuery+"' else o.markedQuery end, "
								+ "p.markedQuery = case when not ANY(x IN p.markedQuery WHERE x = '"+markedQuery+"') then p.markedQuery+'"+markedQuery+"' else p.markedQuery end, "
								+ "q.markedQuery = case when not ANY(x IN q.markedQuery WHERE x = '"+markedQuery+"') then q.markedQuery+'"+markedQuery+"' else q.markedQuery end, "
								+ "n.markedQuery = case when not ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') then n.markedQuery+'"+markedQuery+"' else n.markedQuery end, "
								+ "m.markedQuery = case when not ANY(x IN m.markedQuery WHERE x = '"+markedQuery+"') then m.markedQuery+'"+markedQuery+"' else m.markedQuery end, "
								+ "l.markedQuery = case when not ANY(x IN l.markedQuery WHERE x = '"+markedQuery+"') then l.markedQuery+'"+markedQuery+"' else l.markedQuery end, "
								+ "u.markedQuery = case when not ANY(x IN u.markedQuery WHERE x = '"+markedQuery+"') then u.markedQuery+'"+markedQuery+"' else u.markedQuery end, "
								+ "x.markedQuery = case when not ANY(x IN x.markedQuery WHERE x = '"+markedQuery+"') then x.markedQuery+'"+markedQuery+"' else x.markedQuery end, "
								+ "w.markedQuery = case when not ANY(x IN w.markedQuery WHERE x = '"+markedQuery+"') then w.markedQuery+'"+markedQuery+"' else w.markedQuery end, "
								+ "r.markedQuery = case when not ANY(x IN r.markedQuery WHERE x = '"+markedQuery+"') then r.markedQuery+'"+markedQuery+"' else r.markedQuery end").consume();
					} catch(Exception e){
						System.err.println("Mark Aligned Edges::: "+e.getMessage());
//						if(Math.random() < 0.5)
//						markAlignedEdges(markedQuery, dispatcher) ;
					} finally {markAlignedSession.close();}
					System.out.println(rs.counters().propertiesSet()+" properties were set on nodes and relationships for Double Aligned Edges");
					return true;
				} );
		if(success) 
			{
				markedQueries.add(as.calculateSubGraphBenchmarks((Aligner) AlignerImpl.this , String.valueOf(markedQuery)));
				return markedQuery;	
			}
		else return 0L;	
	}
	}, dispatcher);
	return f;
		
	}

// Aşağıdaki removeInductiveAlignments ile tutarli sonuclar uretmiyor. Yavas calisiyor. Kullanisli degil
// İlk önce tüm hizalama ile Induced Kenarların Güçsüz olanların farkını gönderiyordu. java.util.ConcurrentModificationException ile çok uğraşamadım.
// Şimdi Sadece Induced Kenarların Güçlü olanlarını gönderiyor.
// Çok Az kenar gönderiyor?
public Future<SubGraph> subGraphWithStrongInducedEdges(ExecutionContextExecutor dispatcher, int simTreshold, int annotationTreshold,int powerTreshold){
	
	SubGraph sg = new SubGraph(this);
	Session swcs = AkkaSystem.driver.session();
//	Session ca = AkkaSystem.driver.session();
//	
	Future<SubGraph> f = future(new Callable<SubGraph>() {
		public SubGraph call() {
			
			TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
			SubGraph inducedGraph = template.with(AkkaSystem.graphDb).execute( transaction -> {
				SubGraph sgwcs = sg;
				StatementResult result;
				try ( org.neo4j.driver.v1.Transaction tx = swcs.beginTransaction() )
			      {						
					result = tx.run("match (q:Organism2)-[a2:ALIGNS {alignmentNumber: '"+alignmentNo+"'}]->(n:Organism1)-[INTERACTS_1]-(m:Organism1)<-[a1:ALIGNS {alignmentNumber: '"+alignmentNo+"'}]-(p:Organism2) where not (p)-[:INTERACTS_2]-(q) "
							+ "return a1,a2,n,q,m,p");
					long temp = 0;
					Node n = null, q = null, m = null, p = null;
					Relationship a1 = null,a2 = null;
					while(result.hasNext()){
						Record row = result.next();
						
						for ( Entry<String,Object> column : row.asMap().entrySet() ){
							if(column.getValue()!=null)
								switch (column.getKey()) {
								case "a1":
									a1 = row.get( column.getKey() ).asRelationship();
									break;
								case "a2":
									a2 = row.get( column.getKey() ).asRelationship();
									break;
								case "n":
									n = row.get( column.getKey() ).asNode();
									break;
								case "q":
									q = row.get( column.getKey() ).asNode();
									break;
								case "m":
									m = row.get( column.getKey() ).asNode();
									break;
								case "p":
									p = row.get( column.getKey() ).asNode();
									break;
								default:
									System.out.println("Unexpected column"+column.getKey());
									break;
								}
							}
						temp = compareSimilarityContribution(a1.id(), a2.id(), simTreshold, annotationTreshold, powerTreshold);
						// Ters idyi mi altçizgeye atıyorum, kontrol et?
						if (temp==a1.id())
							{		
									sgwcs.nodes2.add(p);
									sgwcs.nodes1.add(m);
									sgwcs.aligns.add(a1);
							}
						else if (temp==a2.id())
							{
								sgwcs.nodes2.add(q);
								sgwcs.nodes1.add(n);
								sgwcs.aligns.add(a2);
							}
						a1 = null;
						a2 = null;
						temp = 0;
						}
					
					sgwcs.type = "Subgraph with Stronger Induced Edges";
					System.out.println(sgwcs);
					tx.success(); tx.close();
			      } catch (Exception e){
			    	  System.out.println("SubGraph With Strong Induced Edges::: " + e.getMessage());
			    	  subGraphWithStrongInducedEdges(dispatcher, simTreshold, annotationTreshold,powerTreshold);
			      } finally {swcs.close();}
				AlignerImpl.this.conservedStructures = sgwcs;
				return sgwcs; //önceki hali sgwcs 
			} );
			
			return inducedGraph;
		}
		}, dispatcher);
	return f;

}
// Biraz eksik ve yawaş
public Future<Long> markConservedStructures(ExecutionContextExecutor dispatcher,long markedQuery,int simTreshold, int annotationTreshold,int powerTreshold){
	Session mcs = AkkaSystem.driver.session();
	
	
	Future<Long> f = gelecek(new Callable<Long>() {
		public Long call() {
			
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
	boolean success= template.with(AkkaSystem.graphDb).execute( transaction -> {
		StatementResult result;
	try ( org.neo4j.driver.v1.Transaction tx = mcs.beginTransaction() )
    {
		tx.run( "match (p:Organism2)-[t:ALIGNS]->(n:Organism1) where t.alignmentNumber = '"+alignmentNo+"' "
				+ "set t.markedQuery = case when not ANY(x IN t.markedQuery WHERE x = '"+markedQuery+"') then t.markedQuery+'"+markedQuery+"' else t.markedQuery end, "
				+ "p.markedQuery = case when not ANY(x IN p.markedQuery WHERE x = '"+markedQuery+"') then p.markedQuery+'"+markedQuery+"' else p.markedQuery end, "
				+ "n.markedQuery = case when not ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') then n.markedQuery+'"+markedQuery+"' else n.markedQuery end ");
		
		result = tx.run("match (q:Organism2)-[a2:ALIGNS {alignmentNumber: '"+alignmentNo+"'}]->(n:Organism1)-[INTERACTS_1]-(m:Organism1)<-[a1:ALIGNS {alignmentNumber: '"+alignmentNo+"'}]-(p:Organism2) where not (p)-[:INTERACTS_2]-(q) "
				+ "return id(a1),id(a2)");
		
		long a1 = 0;
		long a2 = 0;
		long temp = 0;
		
		while(result.hasNext()){
			Record row = result.next();
			
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null)
					switch (column.getKey()) {
					case "id(a1)":
						a1 = row.get( column.getKey() ).asLong();
						break;
					case "id(a2)":
						a2 = row.get( column.getKey() ).asLong();
						break;
					default:
						System.out.println("Unexpected column"+column.getKey());
						break;
					}
				}
			temp = compareSimilarityContribution(a1, a2,simTreshold, annotationTreshold, powerTreshold);
			if (temp==a1)
				{
					tx.run("match (n:Organism2)-[a2:ALIGNS]->(m:Organism1) where id(a2) ="+a2+" "
							+ "set n.markedQuery = FILTER(x IN n.markedQuery WHERE x <> '"+markedQuery+"'), "
							+ "a2.markedQuery = FILTER(x IN a2.markedQuery WHERE x <> '"+markedQuery+"'), "
							+ "m.markedQuery = FILTER(x IN m.markedQuery WHERE x <> '"+markedQuery+"')");
				}
			else /*if (temp==a)*/
				{
				tx.run("match (n:Organism2)-[a1:ALIGNS]->(m:Organism1) where id(a1) ="+a1+" "
						+ "set n.markedQuery = FILTER(x IN n.markedQuery WHERE x <> '"+markedQuery+"'), "
						+ "a1.markedQuery = FILTER(x IN a1.markedQuery WHERE x <> '"+markedQuery+"'), "
						+ "m.markedQuery = FILTER(x IN m.markedQuery WHERE x <> '"+markedQuery+"')");
				}
			a1 = 0;
			a2 = 0;
			temp = 0;
			}
		
		tx.success(); tx.close();

    } catch (Exception e){
//    	System.out.println("Mark Conserved Structures::: " + e.getMessage());
//    	markConservedStructures(dispatcher,markedQuery);
    } finally {mcs.close();}
	
	return true;
	});
if(success)
return markedQuery;	
else return 0L;


}
}, dispatcher);
return f;
}

// 6 Mayısta güncellendi ama denenmedi
public Future<Long> markConservedStructureQuery(ExecutionContextExecutor dispatcher,long markedQuery){
	
	Future<Long> f = gelecek(new Callable<Long>() {
		public Long call() {
	
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
	boolean success= template.with(AkkaSystem.graphDb).execute( transaction -> {
		ResultSummary rs = null; 
		Session mcsq = AkkaSystem.driver.session();
		try /*( org.neo4j.driver.v1.Transaction tx = mcsq.beginTransaction() )*/
	    {
			rs = mcsq.run("match (q:Organism2)-[a2:ALIGNS {alignmentNumber: '"+alignmentNo+"'}]->(n:Organism1)-[INTERACTS_1]-(m:Organism1)<-[a1:ALIGNS {alignmentNumber: '"+alignmentNo+"'}]-(p:Organism2) where not (p)-[:INTERACTS_2]-(q) "			
	+"optional match (n)-[s2:SIMILARITY]->(q) optional match (m)-[s1:SIMILARITY]->(p) "
	+ "with min(q.power2+q.power3+q.power4 ,n.power2+n.power3+n.power4) as powersum2,min(p.power2+p.power3+p.power4 ,m.power2+m.power3+m.power4) as powersum1,q,a2,n,m,a1,p,s2,s1 " 
	+"set a2.markedQuery = case "
					+ "when length(FILTER(x in q.annotations WHERE x in n.annotations))*s2.similarity+powersum2 > length(FILTER(x in p.annotations WHERE x in m.annotations))*s1.similarity+powersum1 and not ANY(x IN a2.markedQuery WHERE x = '"+markedQuery+"') " 
	                +"then a2.markedQuery+'"+markedQuery+"' "
	                +"when length(FILTER(x in q.annotations WHERE x in n.annotations))*s2.similarity+powersum2 < length(FILTER(x in p.annotations WHERE x in m.annotations))*s1.similarity+powersum1 and not ANY(x IN a2.markedQuery WHERE x = '"+markedQuery+"') "
	                +"then a2.markedQuery "
	                			// Alttaki iki satırdaki ANY durumunu içeren ifadeyi case in başına koyarak markedQuery'e aynı sayının birden fazla defa girilmesi önlenebilir. Ya da bu ifade tamamen kaldırarak markedQery değerinin işaretlenme sayısı ölçülebilir. İşaretlenme sayısı fazla olan eşleşmeler daha değerli olabilir.
	                          +"when ANY(x IN a2.markedQuery WHERE x = '"+markedQuery+"') " 
	                          +"then a2.markedQuery "
	                          +"else case when powersum2 > powersum1 "
	                          +"then a2.markedQuery+'"+markedQuery+"' "
	                          +"when powersum2 < powersum1 "
	                          +"then a2.markedQuery "
	                          +"else case when s2.similarity > s1.similarity "
	                                    +"then a2.markedQuery+'"+markedQuery+"' "
	                                    +"when s2.similarity < s1.similarity "
	                                    +"then a2.markedQuery "
	                                    +"else case when length(FILTER(x in q.annotations WHERE x in n.annotations)) > length(FILTER(x in p.annotations WHERE x in m.annotations)) "
	                                              +"then a2.markedQuery+'"+markedQuery+"' "
	                                              +"when length(FILTER(x in q.annotations WHERE x in n.annotations)) < length(FILTER(x in p.annotations WHERE x in m.annotations)) "
	                                              +"then a2.markedQuery end end end end, " 

	+"q.markedQuery = case "
					+ "when length(FILTER(x in q.annotations WHERE x in n.annotations))*s2.similarity+powersum2 > length(FILTER(x in p.annotations WHERE x in m.annotations))*s1.similarity+powersum1 and not ANY(x IN a2.markedQuery WHERE x = '"+markedQuery+"') " 
	                +"then q.markedQuery+'"+markedQuery+"' "
	                +"when length(FILTER(x in q.annotations WHERE x in n.annotations))*s2.similarity+powersum2 < length(FILTER(x in p.annotations WHERE x in m.annotations))*s1.similarity+powersum1 and not ANY(x IN a2.markedQuery WHERE x = '"+markedQuery+"') "
	                +"then q.markedQuery "
	                          +"when ANY(x IN a2.markedQuery WHERE x = '"+markedQuery+"') "
	                          +"then q.markedQuery "
	                          +"else case when powersum2 > powersum1 "
	                          +"then q.markedQuery+'"+markedQuery+"' "
	                          +"when powersum2 < powersum1 "
	                          +"then q.markedQuery "
	                          +"else case when s2.similarity > s1.similarity "
	                                    +"then q.markedQuery+'"+markedQuery+"' "
	                                    +"when s2.similarity < s1.similarity "
	                                    +"then q.markedQuery "
	                                    +"else case when length(FILTER(x in q.annotations WHERE x in n.annotations)) > length(FILTER(x in p.annotations WHERE x in m.annotations)) "
	                                              +"then q.markedQuery+'"+markedQuery+"' "
	                                              +"when length(FILTER(x in q.annotations WHERE x in n.annotations)) < length(FILTER(x in p.annotations WHERE x in m.annotations)) "
	                                              +"then q.markedQuery end end end end, "

	+"n.markedQuery = case "
					 + "when length(FILTER(x in q.annotations WHERE x in n.annotations))*s2.similarity+powersum2 > length(FILTER(x in p.annotations WHERE x in m.annotations))*s1.similarity+powersum1 and not ANY(x IN a2.markedQuery WHERE x = '"+markedQuery+"') " 
	                 +"then n.markedQuery+'"+markedQuery+"' "
	                 +"when length(FILTER(x in q.annotations WHERE x in n.annotations))*s2.similarity+powersum2 < length(FILTER(x in p.annotations WHERE x in m.annotations))*s1.similarity+powersum1 and not ANY(x IN a2.markedQuery WHERE x = '"+markedQuery+"') "
	                 +"then n.markedQuery "
	                          +"when ANY(x IN a2.markedQuery WHERE x = '"+markedQuery+"') " 
	                          +"then n.markedQuery "
	                          +"else case when powersum2 > powersum1 "
	                          +"then n.markedQuery+'"+markedQuery+"' "
	                          +"when powersum2 < powersum1 "
	                          +"then n.markedQuery "
	                          +"else case when s2.similarity > s1.similarity "
	                                    +"then n.markedQuery+'"+markedQuery+"' "
	                                    +"when s2.similarity < s1.similarity "
	                                    +"then n.markedQuery "
	                                    +"else case when length(FILTER(x in q.annotations WHERE x in n.annotations)) > length(FILTER(x in p.annotations WHERE x in m.annotations)) "
	                                              +"then n.markedQuery+'"+markedQuery+"' "
	                                              +"when length(FILTER(x in q.annotations WHERE x in n.annotations)) < length(FILTER(x in p.annotations WHERE x in m.annotations)) "
	                                              +"then n.markedQuery end end end end, "

	+"a1.markedQuery = case "
					+ "when length(FILTER(x in q.annotations WHERE x in n.annotations))*s2.similarity+powersum2 > length(FILTER(x in p.annotations WHERE x in m.annotations))*s1.similarity+powersum1 and not ANY(x IN a2.markedQuery WHERE x = '"+markedQuery+"') " 
	                +"then a1.markedQuery "
	                +"when length(FILTER(x in q.annotations WHERE x in n.annotations))*s2.similarity+powersum2 < length(FILTER(x in p.annotations WHERE x in m.annotations))*s1.similarity+powersum1 and not ANY(x IN a2.markedQuery WHERE x = '"+markedQuery+"') "
	                +"then a1.markedQuery+'"+markedQuery+"' "
	                          +"when ANY(x IN a2.markedQuery WHERE x = '"+markedQuery+"') "
	                          +"then a1.markedQuery "
	                          +"else case when powersum2 > powersum1 "
	                          +"then a1.markedQuery+'"+markedQuery+"' "
	                          +"when powersum2 < powersum1 "
	                          +"then a1.markedQuery "
	                          +"else case when s2.similarity > s1.similarity "
	                                    +"then a1.markedQuery "
	                                    +"when s2.similarity < s1.similarity "
	                                    +"then a1.markedQuery+'"+markedQuery+"' "
	                                    +"else case when length(FILTER(x in q.annotations WHERE x in n.annotations)) > length(FILTER(x in p.annotations WHERE x in m.annotations)) "
	                                              +"then a1.markedQuery "
	                                              +"when length(FILTER(x in q.annotations WHERE x in n.annotations)) < length(FILTER(x in p.annotations WHERE x in m.annotations)) "
	                                              +"then a1.markedQuery+'"+markedQuery+"' end end end end, "

	+"p.markedQuery = case "
					+ "when length(FILTER(x in q.annotations WHERE x in n.annotations))*s2.similarity+powersum2 > length(FILTER(x in p.annotations WHERE x in m.annotations))*s1.similarity+powersum1 and not ANY(x IN a2.markedQuery WHERE x = '"+markedQuery+"') " 
	                +"then p.markedQuery "
	                +"when length(FILTER(x in q.annotations WHERE x in n.annotations))*s2.similarity+powersum2 < length(FILTER(x in p.annotations WHERE x in m.annotations))*s1.similarity+powersum1 and not ANY(x IN a2.markedQuery WHERE x = '"+markedQuery+"') "
	                +"then p.markedQuery+'"+markedQuery+"' "
	                          +"when ANY(x IN a2.markedQuery WHERE x = '"+markedQuery+"') "
	                          +"then p.markedQuery "
	                          +"else case when powersum2 > powersum1 "
	                          +"then p.markedQuery+'"+markedQuery+"' "
	                          +"when powersum2 < powersum1 "
	                          +"then p.markedQuery "
	                          +"else case when s2.similarity > s1.similarity "
	                                    +"then p.markedQuery "
	                                    +"when s2.similarity < s1.similarity "
	                                    +"then p.markedQuery+'"+markedQuery+"' "
	                                    +"else case when length(FILTER(x in q.annotations WHERE x in n.annotations)) > length(FILTER(x in p.annotations WHERE x in m.annotations)) "
	                                              +"then p.markedQuery "
	                                              +"when length(FILTER(x in q.annotations WHERE x in n.annotations)) < length(FILTER(x in p.annotations WHERE x in m.annotations)) "
	                                              +"then p.markedQuery+'"+markedQuery+"' end end end end, "

	+"m.markedQuery = case "
					+ "when length(FILTER(x in q.annotations WHERE x in n.annotations))*s2.similarity+powersum2 > length(FILTER(x in p.annotations WHERE x in m.annotations))*s1.similarity+powersum1 and not ANY(x IN a2.markedQuery WHERE x = '"+markedQuery+"') " 
	                +"then m.markedQuery "
	                +"when length(FILTER(x in q.annotations WHERE x in n.annotations))*s2.similarity+powersum2 < length(FILTER(x in p.annotations WHERE x in m.annotations))*s1.similarity+powersum1 and not ANY(x IN a2.markedQuery WHERE x = '"+markedQuery+"') "
	                +"then m.markedQuery+'"+markedQuery+"' "
	                          +"when ANY(x IN a2.markedQuery WHERE x = '"+markedQuery+"') "
	                          +"then m.markedQuery "
	                          +"else case when powersum2 > powersum1 "
	                          +"then m.markedQuery+'"+markedQuery+"' "
	                          +"when powersum2 < powersum1 "
	                          +"then m.markedQuery "
	                          +"else case when s2.similarity > s1.similarity "
	                                    +"then m.markedQuery "
	                                    +"when s2.similarity < s1.similarity "
	                                    +"then m.markedQuery+'"+markedQuery+"' "
	                                    +"else case when length(FILTER(x in q.annotations WHERE x in n.annotations)) > length(FILTER(x in p.annotations WHERE x in m.annotations)) "
	                                              +"then m.markedQuery "
	                                              +"when length(FILTER(x in q.annotations WHERE x in n.annotations)) < length(FILTER(x in p.annotations WHERE x in m.annotations)) "
	                                              +"then m.markedQuery+'"+markedQuery+"' end end end end").consume();
//			tx.success(); tx.close();

	    } catch (Exception e){
	    	System.err.println("Mark Conserved Structure Query::: " + e.getMessage());
//	    	if(Math.random() < 0.5)
//	    	markConservedStructureQuery(dispatcher,markedQuery);
	    } finally {mcsq.close();}
		System.out.println(rs.counters().propertiesSet()+" properties were set on nodes and relationships for Conserved Structure Query");
		return true;
		});
	if(success) 
	{
		markedQueries.add(as.calculateSubGraphBenchmarks((Aligner) AlignerImpl.this , String.valueOf(markedQuery)));
		return markedQuery;	
	}
	else return 0L;	
}
}, dispatcher);
return f;
}

public Future<SubGraph> subGraphWithXBitScoreSimilarity(double treshold, ExecutionContextExecutor dispatcher){
	SubGraph s = new SubGraph(this);
	Session  sgwxbss= AkkaSystem.driver.session();
	Future<SubGraph> f = future(new Callable<SubGraph>() {
		public SubGraph call() {
			TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
			SubGraph success = template.with(AkkaSystem.graphDb).execute( transaction -> {
				StatementResult result;
				SubGraph sgwxbs = s;
				try ( org.neo4j.driver.v1.Transaction tx = sgwxbss.beginTransaction() )
			    {
					result = tx.run("match (n:Organism1)<-[r:ALIGNS]-(m:Organism2)<-[s:SIMILARITY]-(n) where r.alignmentNumber = '"+alignmentNo+"' and s.similarity >= "+treshold+" return (s),(m),(n),(r)");
					
					
					while(result.hasNext()){
						Record row = result.next();
						for ( Entry<String,Object> column : row.asMap().entrySet() ){
							if(column.getValue()!=null)
								switch (column.getKey()){
							case "s":
								sgwxbs.similarity.add((Relationship) row.get( column.getKey() ).asRelationship());
								break;
							case "m":
								sgwxbs.nodes2.add((Node) row.get( column.getKey() ).asNode());
								break;
							case "n":
								sgwxbs.nodes1.add((Node) row.get( column.getKey() ).asNode());
								break;
							case "r":
								sgwxbs.aligns.add((Relationship) row.get( column.getKey() ).asRelationship());
								break;
							default:
								break;
							}		
							System.out.printf( "%s = %s%n", column.getKey() , row.get(column.getKey() ) );
					}
						sgwxbs.type = "SubGraph with "+treshold+" Bitscore Similarity";
						tx.success(); tx.close();
						
			    }
					} catch (Exception e){
						System.out.println("SubGraph With X Bit Score Similarity::: " + e.getMessage());
				    	subGraphWithXBitScoreSimilarity(treshold,dispatcher);
			    } finally {sgwxbss.close();}	
				AlignerImpl.this.xBitScore = sgwxbs;
				return sgwxbs;
			} );
				
			return success;
		}
		}, dispatcher);
//	this.xBitScore = sgwxbs;	
	return f;
}

public Future<Long> markXBitScoreSimilarity(double treshold, long markedQuery, ExecutionContextExecutor dispatcher){
	
	Future<Long> f = gelecek(new Callable<Long>() {
		public Long call() {
			
			TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
			tm.failure(new Throwable("Herkesin tuttuğu kendine"));
			TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
			boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
				ResultSummary rs = null;
				Session markXBitScoreSession = AkkaSystem.driver.session();
				try{
					rs = markXBitScoreSession.run("match (n:Organism1)<-[r:ALIGNS]-(m:Organism2)<-[s:SIMILARITY]-(n) where r.alignmentNumber = '"+alignmentNo+"' and s.similarity >= "+treshold+" "
							+ "set s.markedQuery = case when not ANY(x IN s.markedQuery WHERE x = '"+markedQuery+"') then s.markedQuery+'"+markedQuery+"' else s.markedQuery end, "
							+ "m.markedQuery = case when not ANY(x IN m.markedQuery WHERE x = '"+markedQuery+"') then m.markedQuery+'"+markedQuery+"' else m.markedQuery end, "
							+ "n.markedQuery = case when not ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') then n.markedQuery+'"+markedQuery+"' else n.markedQuery end, "
							+ "r.markedQuery = case when not ANY(x IN r.markedQuery WHERE x = '"+markedQuery+"') then r.markedQuery+'"+markedQuery+"' else r.markedQuery end").consume();
				} catch(Exception e){
					System.err.println("Mark X BitScore Similarity::: "+e.getMessage());
//					if(Math.random() < 0.5)
//					markXBitScoreSimilarity(treshold, markedQuery, dispatcher);
				} finally {markXBitScoreSession.close();}
				System.out.println(rs.counters().propertiesSet()+" properties were set on nodes and relationships for BitScore");
				return true;
			} );
			

			if(success) 
			{
				markedQueries.add(as.calculateSubGraphBenchmarks((Aligner) AlignerImpl.this , String.valueOf(markedQuery)));
				return markedQuery;	
			}
	else return 0L;	
}
}, dispatcher);
return f;
}

public Future<SubGraph> subGraphWithKGOTerms (int k, ExecutionContextExecutor dispatcher){
	
	SubGraph s = new SubGraph(this);
	Session  sgwkgts = AkkaSystem.driver.session();
	Future<SubGraph> f = future(new Callable<SubGraph>() {
		public SubGraph call() {
			
			TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
			SubGraph success = template.with(AkkaSystem.graphDb).execute( transaction -> {
				StatementResult result;
				SubGraph sgwKGT = s;
		
		try ( org.neo4j.driver.v1.Transaction tx = sgwkgts.beginTransaction())
	    {
			result = tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where r.alignmentNumber ='"+alignmentNo+"' and length(FILTER(x in n.annotations WHERE x in m.annotations)) >= "+k+" return (n),(r),(m)");
			while(result.hasNext()){
				Record row = result.next();
				for ( Entry<String,Object> column : row.asMap().entrySet() ){
					if(column.getValue()!=null)
						switch (column.getKey()) {
						case "n":
							sgwKGT.nodes2.add((Node) row.get( column.getKey() ).asNode());
							break;
						case "r":
							sgwKGT.aligns.add((Relationship) row.get( column.getKey() ).asRelationship());
							break;
						case "m":
							sgwKGT.nodes1.add((Node) row.get( column.getKey() ).asNode());
							break;
						default:
							System.out.println("Unexpected column");
							break;
						}
				}
			}
			sgwKGT.type = "SubGraph with "+k+" GO Terms";
			tx.success(); tx.close();
	    } catch (Exception e){
	    	System.out.println("SubGraph With K GO Terms::: " + e.getMessage());
	    	subGraphWithKGOTerms(k,dispatcher);
	      } finally {sgwkgts.close();}
		AlignerImpl.this.kGOT = sgwKGT;
				return sgwKGT;
			} );	
	return success;
		}
		}, dispatcher);
	return f;
}

public Future<Long> markKGOTerms (int k, long markedQuery, ExecutionContextExecutor dispatcher){
	
	Future<Long> f = gelecek(new Callable<Long>() {
		public Long call() {
			
			TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
			tm.failure(new Throwable("Herkesin tuttuğu kendine"));
			TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
			boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
				ResultSummary rs = null;
				Session markKGOTermsSession = AkkaSystem.driver.session();
				try{
				rs =	markKGOTermsSession.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where r.alignmentNumber ='"+alignmentNo+"' and length(FILTER(x in n.annotations WHERE x in m.annotations)) >= "+k+" "
							+ "set m.markedQuery = case when not ANY(x IN m.markedQuery WHERE x = '"+markedQuery+"') then m.markedQuery+'"+markedQuery+"' else m.markedQuery end, "
							+ "n.markedQuery = case when not ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') then n.markedQuery+'"+markedQuery+"' else n.markedQuery end, "
							+ "r.markedQuery = case when not ANY(x IN r.markedQuery WHERE x = '"+markedQuery+"') then r.markedQuery+'"+markedQuery+"' else r.markedQuery end").consume();
				} catch(Exception e){
					System.err.println("Mark K GO Terms::: "+e.getMessage());
//					if(Math.random() < 0.5)
//						markKGOTerms(k, markedQuery,dispatcher);
				} finally {markKGOTermsSession.close();}
				System.out.println(rs.counters().propertiesSet()+" properties were set on nodes and relationships for GO Terms");
				return true;
			} );
			if(success) 
			{
				markedQueries.add(as.calculateSubGraphBenchmarks((Aligner) AlignerImpl.this , String.valueOf(markedQuery)));
				return markedQuery;	
			}
	else return 0L;	
}
}, dispatcher);
return f;
	
}
// Bunlara çalışıcaz daha
// laddDivisorı 100 yapmak iyi oluyor.
// options are: power, betweenness, harmonic, pagerank and closeness
public Future<SubGraph> subGraphWithTopAlignedPowerNodes(int limit, String algorithm, ExecutionContextExecutor dispatcher){
	SubGraph s = new SubGraph(this);
	Session  sgwapn = AkkaSystem.driver.session();
	Future<SubGraph> f = future(new Callable<SubGraph>() {
		public SubGraph call() {
			
			TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
			SubGraph success = template.with(AkkaSystem.graphDb).execute( transaction -> {
				StatementResult result;
				SubGraph sgwAPN = s;
		
		try ( org.neo4j.driver.v1.Transaction tx = sgwapn.beginTransaction())
	    {
			// match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where r.alignmentNumber = '9' return n.power2+n.power3+n.power4 as s1, m.power2+m.power3+m.power4 as s2 order by (s1+s2)/(abs(s1-s2)+100) desc limit 5
			// // match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where r.alignmentNumber = '9' return n.power2,n.power3,n.power4,m.power2,m.power3,m.power4,n.power2+n.power3+n.power4 as s1, m.power2+m.power3+m.power4 as s2 order by (s1+s2)/(abs(s1-s2)+100) desc limit 5
			if(algorithm.equals("power"))
			result = tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where r.alignmentNumber = '"+AlignerImpl.this.alignmentNo+"' with n,r,m,n.power2+n.power3+n.power4 as s1, m.power2+m.power3+m.power4 as s2 return n,r,m,min(s1,s2) order by min(s1,s2) desc limit "+limit);
			else if (algorithm.equals("betweenness")||algorithm.equals("harmonic")||algorithm.equals("pagerank")||algorithm.equals("closeness"))
				result = tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where r.alignmentNumber = '"+AlignerImpl.this.alignmentNo+"' with n,r,m,n."+algorithm+" as s1, m."+algorithm+" as s2 return n,r,m,min(s1,s2) order by min(s1,s2) desc limit "+limit);
			else
				result = null;
			while(result.hasNext()){
				Record row = result.next();
				for ( Entry<String,Object> column : row.asMap().entrySet() ){
					if(column.getValue()!=null)
						switch (column.getKey()) {
						case "n":
							sgwAPN.nodes2.add((Node) row.get( column.getKey() ).asNode());
							break;
						case "r":
							sgwAPN.aligns.add((Relationship) row.get( column.getKey() ).asRelationship());
							break;
						case "m":
							sgwAPN.nodes1.add((Node) row.get( column.getKey() ).asNode());
							break;
						case "min(s1,s2)":
							;
							break;
						default:
							System.out.println("Unexpected column");
							break;
						}
				}
			}
			sgwAPN.type = "SubGraph with "+limit +" Top PowerNode Pairs";
			tx.success(); tx.close();
	    } catch (Exception e){
	    	System.out.println("SubGraph Power Nodes::: " + e.getMessage());
	    	subGraphWithTopAlignedPowerNodes(limit,algorithm, dispatcher);
	      } finally {sgwapn.close();}
		AlignerImpl.this.powerNodes = sgwAPN;
				return sgwAPN;
			} );	
	return success;
		}
		}, dispatcher);
	return f;
}

public Future<Long> markTopAlignedPowerNodes(int limit, long markedQuery, String algorithm, ExecutionContextExecutor dispatcher){
	Future<Long> f = gelecek(new Callable<Long>() {
		public Long call() {
			
			TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
			tm.failure(new Throwable("Herkesin tuttuğu kendine"));
			TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
			boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
				ResultSummary rs = null;
				Session markAPNSession = AkkaSystem.driver.session();
				try{
					
					// match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where r.alignmentNumber = '9' with n,r,m,n.power2+n.power3+n.power4 as s1, m.power2+m.power3+m.power4 as s2 order by (s1+s2)/(abs(s1-s2)+100) desc limit 10 set r.markedQuery = r.markedQuery + '999'
					if(algorithm.equals("power"))
				rs =	markAPNSession.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where r.alignmentNumber = '"+AlignerImpl.this.alignmentNo+"' with n,r,m,n.power2+n.power3+n.power4 as s1, m.power2+m.power3+m.power4 as s2 order by min(s1,s2) desc limit "+limit
							+ " set m.markedQuery = case when not ANY(x IN m.markedQuery WHERE x = '"+markedQuery+"') then m.markedQuery+'"+markedQuery+"' else m.markedQuery end, "
							+ "n.markedQuery = case when not ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') then n.markedQuery+'"+markedQuery+"' else n.markedQuery end, "
							+ "r.markedQuery = case when not ANY(x IN r.markedQuery WHERE x = '"+markedQuery+"') then r.markedQuery+'"+markedQuery+"' else r.markedQuery end").consume();
					else if (algorithm.equals("betweenness")||algorithm.equals("harmonic")||algorithm.equals("pagerank")||algorithm.equals("closeness"))
					rs =	markAPNSession.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where r.alignmentNumber = '"+AlignerImpl.this.alignmentNo+"' with n,r,m,n."+algorithm+" as s1, m."+algorithm+" as s2 order by min(s1,s2) desc limit "+limit
							+ " set m.markedQuery = case when not ANY(x IN m.markedQuery WHERE x = '"+markedQuery+"') then m.markedQuery+'"+markedQuery+"' else m.markedQuery end, "
							+ "n.markedQuery = case when not ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') then n.markedQuery+'"+markedQuery+"' else n.markedQuery end, "
							+ "r.markedQuery = case when not ANY(x IN r.markedQuery WHERE x = '"+markedQuery+"') then r.markedQuery+'"+markedQuery+"' else r.markedQuery end").consume();
				} catch(Exception e){
					System.err.println("Mark Top Power Nodes::: "+e.getMessage());
//					if(Math.random() < 0.5)
//						markKGOTerms(k, markedQuery,dispatcher);
				} finally {markAPNSession.close();}
				System.out.println(rs.counters().propertiesSet()+" properties were set on nodes and relationships for Power Nodes");
				return true;
			} );
			if(success) 
			{
				markedQueries.add(as.calculateSubGraphBenchmarks((Aligner) AlignerImpl.this , String.valueOf(markedQuery)));
				return markedQuery;	
			}
	else return 0L;	
}
}, dispatcher);
return f;
}

public Future<SubGraph> subGraphWithAlignedPowerNodes(int minCommonAnnotations, int power2, int power3, int power4, ExecutionContextExecutor dispatcher){
	SubGraph s = new SubGraph(this);
	Session  sgwapn = AkkaSystem.driver.session();
	Future<SubGraph> f = future(new Callable<SubGraph>() {
		public SubGraph call() {
			
			TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
			SubGraph success = template.with(AkkaSystem.graphDb).execute( transaction -> {
				StatementResult result;
				SubGraph sgwAPN = s;
		
		try ( org.neo4j.driver.v1.Transaction tx = sgwapn.beginTransaction())
	    {
			result = tx.run("match (p:Organism2)<-[t:SIMILARITY]-(n:Organism1)<-[a:ALIGNS]-(p) where length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" and p.power2 >= "+power2+" and n.power2 >= "+power2+" and p.power3 >= "+power3+" and n.power3 >= "+power3+" and p.power4 >= "+power4+" and n.power4 >= "+power4+" return p,n,a order by n.power4+p.power4 desc,n.power3+p.power3 desc,n.power2+p.power2 desc");
			while(result.hasNext()){
				Record row = result.next();
				for ( Entry<String,Object> column : row.asMap().entrySet() ){
					if(column.getValue()!=null)
						switch (column.getKey()) {
						case "p":
							sgwAPN.nodes2.add((Node) row.get( column.getKey() ).asNode());
							break;
						case "a":
							sgwAPN.aligns.add((Relationship) row.get( column.getKey() ).asRelationship());
							break;
						case "n":
							sgwAPN.nodes1.add((Node) row.get( column.getKey() ).asNode());
							break;
						default:
							System.out.println("Unexpected column");
							break;
						}
				}
			}
			sgwAPN.type = "SubGraph with 2,3,4 PowerNodes type and "+power2+" & "+power3+" & "+power4+" PowerNode Values Respectively";
			tx.success(); tx.close();
	    } catch (Exception e){
	    	System.out.println("SubGraph Power Nodes::: " + e.getMessage());
	    	subGraphWithAlignedPowerNodes(minCommonAnnotations, power2, power3, power4, dispatcher);
	      } finally {sgwapn.close();}
		AlignerImpl.this.powerNodes = sgwAPN;
				return sgwAPN;
			} );	
	return success;
		}
		}, dispatcher);
	return f;
}

public Future<Long> markAlignedPowerNodes(int minCommonAnnotations, int power2, int power3, int power4, long markedQuery, ExecutionContextExecutor dispatcher){
	Future<Long> f = gelecek(new Callable<Long>() {
		public Long call() {
			
			TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
			tm.failure(new Throwable("Herkesin tuttuğu kendine"));
			TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
			boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
				ResultSummary rs = null;
				Session markAPNSession = AkkaSystem.driver.session();
				try{
				rs =	markAPNSession.run("match (p:Organism2)<-[t:SIMILARITY]-(n:Organism1)<-[a:ALIGNS]-(p) where length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" and p.power2 >= "+power2+" and n.power2 >= "+power2+" and p.power3 >= "+power3+" and n.power3 >= "+power3+" and p.power4 >= "+power4+" and n.power4 >= "+power4
							+ " set p.markedQuery = case when not ANY(x IN p.markedQuery WHERE x = '"+markedQuery+"') then p.markedQuery+'"+markedQuery+"' else p.markedQuery end, "
							+ "n.markedQuery = case when not ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') then n.markedQuery+'"+markedQuery+"' else n.markedQuery end, "
							+ "a.markedQuery = case when not ANY(x IN a.markedQuery WHERE x = '"+markedQuery+"') then a.markedQuery+'"+markedQuery+"' else a.markedQuery end").consume();
				} catch(Exception e){
					System.err.println("Mark Power Nodes::: "+e.getMessage());
//					if(Math.random() < 0.5)
//						markKGOTerms(k, markedQuery,dispatcher);
				} finally {markAPNSession.close();}
				System.out.println(rs.counters().propertiesSet()+" properties were set on nodes and relationships for Power Nodes");
				return true;
			} );
			if(success) 
			{
				markedQueries.add(as.calculateSubGraphBenchmarks((Aligner) AlignerImpl.this , String.valueOf(markedQuery)));
				return markedQuery;	
			}
	else return 0L;	
}
}, dispatcher);
return f;
}

public Future<SubGraph> subGraphWithAlternativeCentralNodes(int minCommonAnnotations, double score1, double score2, String algorithm,ExecutionContextExecutor dispatcher){
	SubGraph s = new SubGraph(this);
	Session  sgwapn = AkkaSystem.driver.session();
	Future<SubGraph> f = future(new Callable<SubGraph>() {
		public SubGraph call() {
			
			TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
			SubGraph success = template.with(AkkaSystem.graphDb).execute( transaction -> {
				StatementResult result;
				SubGraph sgwAPN = s;
		
		try ( org.neo4j.driver.v1.Transaction tx = sgwapn.beginTransaction())
	    {
			result = tx.run("match (p:Organism2)<-[t:SIMILARITY]-(n:Organism1)<-[a:ALIGNS]-(p) where length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" and  p."+algorithm+" >"+score2+" and n."+algorithm+" >"+score1+" return p,n,a order by p."+algorithm+"+n."+algorithm+", length(FILTER(x in p.annotations WHERE x in n.annotations)) desc");
			while(result.hasNext()){
				Record row = result.next();
				for ( Entry<String,Object> column : row.asMap().entrySet() ){
					if(column.getValue()!=null)
						switch (column.getKey()) {
						case "p":
							sgwAPN.nodes2.add((Node) row.get( column.getKey() ).asNode());
							break;
						case "a":
							sgwAPN.aligns.add((Relationship) row.get( column.getKey() ).asRelationship());
							break;
						case "n":
							sgwAPN.nodes1.add((Node) row.get( column.getKey() ).asNode());
							break;
						default:
							System.out.println("Unexpected column");
							break;
						}
				}
			}
			sgwAPN.type = "SubGraph with "+algorithm+" CentralNodes type and "+score1+" & "+score2+" CentralNode Values Respectively";
			tx.success(); tx.close();
	    } catch (Exception e){
	    	System.out.println("SubGraph Power Nodes::: " + e.getMessage());
	    	subGraphWithAlternativeCentralNodes(minCommonAnnotations, score1,score2, algorithm,dispatcher);
	      } finally {sgwapn.close();}
		AlignerImpl.this.powerNodes = sgwAPN;
				return sgwAPN;
			} );	
	return success;
		}
		}, dispatcher);
	return f;
}

public Future<Long> markAlternativeCentralNodes(int minCommonAnnotations, double score1, double score2, String algorithm,long markedQuery, ExecutionContextExecutor dispatcher){
	Future<Long> f = gelecek(new Callable<Long>() {
		public Long call() {
			
			TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
			tm.failure(new Throwable("Herkesin tuttuğu kendine"));
			TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
			boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
				ResultSummary rs = null;
				Session markKGOTermsSession = AkkaSystem.driver.session();
				try{
				rs =	markKGOTermsSession.run("match (p:Organism2)<-[t:SIMILARITY]-(n:Organism1)<-[a:ALIGNS]-(p) where length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" and  p."+algorithm+" >"+score2+" and n."+algorithm+" >"+score1
							+ " set p.markedQuery = case when not ANY(x IN p.markedQuery WHERE x = '"+markedQuery+"') then p.markedQuery+'"+markedQuery+"' else p.markedQuery end, "
							+ "n.markedQuery = case when not ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') then n.markedQuery+'"+markedQuery+"' else n.markedQuery end, "
							+ "a.markedQuery = case when not ANY(x IN a.markedQuery WHERE x = '"+markedQuery+"') then a.markedQuery+'"+markedQuery+"' else a.markedQuery end").consume();
				} catch(Exception e){
					System.err.println("Mark K GO Terms::: "+e.getMessage());
//					if(Math.random() < 0.5)
//						markKGOTerms(k, markedQuery,dispatcher);
				} finally {markKGOTermsSession.close();}
				System.out.println(rs.counters().propertiesSet()+" properties were set on nodes and relationships for "+algorithm+" Central Nodes");
				return true;
			} );
			if(success) 
			{
				markedQueries.add(as.calculateSubGraphBenchmarks((Aligner) AlignerImpl.this , String.valueOf(markedQuery)));
				return markedQuery;	
			}
	else return 0L;	
}
}, dispatcher);
return f;
}

public Future<SubGraph> subGraphWithClusterEdges(int minCommonAnnotations, String clusterType,long clusterIDOfOrganism1, long clusterIDOfOrganism2,ExecutionContextExecutor dispatcher){
	SubGraph s = new SubGraph(this);
	Session  sgwces = AkkaSystem.driver.session();
	Future<SubGraph> f = future(new Callable<SubGraph>() {
		public SubGraph call() {
			
			TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
			SubGraph success = template.with(AkkaSystem.graphDb).execute( transaction -> {
				StatementResult result;
				SubGraph sgwCE = s;
		
		try ( org.neo4j.driver.v1.Transaction tx = sgwces.beginTransaction())
	    {
			result = tx.run("match (n:Organism2 {"+clusterType+": "+clusterIDOfOrganism2+"})-[a1:ALIGNS]->(m:Organism1 {"+clusterType+": "+clusterIDOfOrganism1+"})"
					+ "-[i1:INTERACTS_1]-(l:Organism1 {"+clusterType+": "+clusterIDOfOrganism1+"})<-[a2:ALIGNS]-(o:Organism2 {"+clusterType+": "+clusterIDOfOrganism2+"})-[i2:INTERACTS_2]-(n) "
					+ "where length(FILTER(x in n.annotations WHERE x in m.annotations)) >= "+minCommonAnnotations+" "
					+ "and length(FILTER(x in o.annotations WHERE x in l.annotations)) >= "+minCommonAnnotations+" return n,a1,m,l,a2,o");;
			while(result.hasNext()){
				Record row = result.next();
				for ( Entry<String,Object> column : row.asMap().entrySet() ){
					if(column.getValue()!=null)
						switch (column.getKey()) {
						case "n":
							sgwCE.nodes2.add((Node) row.get( column.getKey() ).asNode());
							break;
						case "a1":
							sgwCE.aligns.add((Relationship) row.get( column.getKey() ).asRelationship());
							break;
						case "m":
							sgwCE.nodes1.add((Node) row.get( column.getKey() ).asNode());
							break;
						case "l":
							sgwCE.nodes1.add((Node) row.get( column.getKey() ).asNode());
							break;
						case "a2":
							sgwCE.aligns.add((Relationship) row.get( column.getKey() ).asRelationship());
							break;
						case "o":
							sgwCE.nodes2.add((Node) row.get( column.getKey() ).asNode());
							break;
						default:
							System.out.println("Unexpected column");
							break;
						}
				}
			}
			sgwCE.type = "SubGraph with "+clusterType+" type and "+clusterIDOfOrganism1+" & "+clusterIDOfOrganism2+" Cluster Edges";
			tx.success(); tx.close();
	    } catch (Exception e){
	    	System.out.println("SubGraph With Cluster Edges::: " + e.getMessage());
	    	subGraphWithClusterEdges(minCommonAnnotations, clusterType,clusterIDOfOrganism1, clusterIDOfOrganism2,dispatcher);
	      } finally {sgwces.close();}
		AlignerImpl.this.clusterEdges = sgwCE;
				return sgwCE;
			} );	
	return success;
		}
		}, dispatcher);
	return f;
}

public Future<Long> markClusterEdges(int minCommonAnnotations, String clusterType,long clusterIDOfOrganism1, long clusterIDOfOrganism2,long markedQuery, ExecutionContextExecutor dispatcher){
	Future<Long> f = gelecek(new Callable<Long>() {
		public Long call() {
			
			TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
			tm.failure(new Throwable("Herkesin tuttuğu kendine"));
			TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
			boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
				ResultSummary rs = null;
				Session markClusterEdgesSession = AkkaSystem.driver.session();
				try{
				rs =	markClusterEdgesSession.run("match (n:Organism2 {"+clusterType+": "+clusterIDOfOrganism2+"})-[a1:ALIGNS]->(m:Organism1 {"+clusterType+": "+clusterIDOfOrganism1+"})"
						+ "-[i1:INTERACTS_1]-(l:Organism1 {"+clusterType+": "+clusterIDOfOrganism1+"})<-[a2:ALIGNS]-(o:Organism2 {"+clusterType+": "+clusterIDOfOrganism2+"})-[i2:INTERACTS_2]-(n) "
						+ "where length(FILTER(x in n.annotations WHERE x in m.annotations)) >= "+minCommonAnnotations+" "
						+ "and length(FILTER(x in o.annotations WHERE x in l.annotations)) >= "+minCommonAnnotations+" " 
							+ "set m.markedQuery = case when not ANY(x IN m.markedQuery WHERE x = '"+markedQuery+"') then m.markedQuery+'"+markedQuery+"' else m.markedQuery end, "
							+ "n.markedQuery = case when not ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') then n.markedQuery+'"+markedQuery+"' else n.markedQuery end, "
							+ "a1.markedQuery = case when not ANY(x IN a1.markedQuery WHERE x = '"+markedQuery+"') then a1.markedQuery+'"+markedQuery+"' else a1.markedQuery end, "
							+ "l.markedQuery = case when not ANY(x IN l.markedQuery WHERE x = '"+markedQuery+"') then l.markedQuery+'"+markedQuery+"' else l.markedQuery end, "
							+ "o.markedQuery = case when not ANY(x IN o.markedQuery WHERE x = '"+markedQuery+"') then o.markedQuery+'"+markedQuery+"' else o.markedQuery end, "
							+ "a2.markedQuery = case when not ANY(x IN a2.markedQuery WHERE x = '"+markedQuery+"') then a2.markedQuery+'"+markedQuery+"' else a2.markedQuery end").consume();
				} catch(Exception e){
					System.err.println("Mark Cluster Edges::: "+e.getMessage());
//					if(Math.random() < 0.5)
//						markKGOTerms(k, markedQuery,dispatcher);
				} finally {markClusterEdgesSession.close();}
				System.out.println(rs.counters().propertiesSet()+" properties were set on nodes and relationships for Cluster Edges");
				return true;
			} );
			if(success) 
			{
				markedQueries.add(as.calculateSubGraphBenchmarks((Aligner) AlignerImpl.this , String.valueOf(markedQuery)));
				return markedQuery;	
			}
	else return 0L;	
}
}, dispatcher);
return f;
}

public void increaseBitScoreAndGOC(int minCommonAnnotations, char mode){
	System.out.println("increaseBitScoreAndGOC for Aligner "+this.alignmentNo+" with "+minCommonAnnotations+" annotations");
	
	ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
	ArrayList<Node> record = new ArrayList<Node>();
	Set<Node> aligned = new HashSet<Node>();
	Session bsgoc = AkkaSystem.driver.session();
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
	boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
		StatementResult result;
		int countRows = 0;
		try ( org.neo4j.driver.v1.Transaction tx = bsgoc.beginTransaction() ){		
		markUnalignedNodes();
		
		result = tx.run("match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)<-[t:SIMILARITY]-(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[s:SIMILARITY]->(o) where ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN o.marked WHERE x = '"+this.alignmentNo+"') and length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" and length(FILTER(x in o.annotations WHERE x in m.annotations)) >="+minCommonAnnotations+" return o,m,p,n order by t.similarity+s.similarity desc");
		while(result.hasNext()){
			Record row = result.next();
			record.clear();
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null)
					switch (column.getKey()) {
					case "o":
						record.add(0,row.get( column.getKey() ).asNode());
						break;
					case "m":
						record.add(1,row.get( column.getKey() ).asNode());
						break;
					case "p":
						record.add(2,row.get( column.getKey() ).asNode());
						break;
					case "n":
						record.add(3,row.get( column.getKey() ).asNode());
						break;
					default:
						System.out.println("Unexpected column"+column.getKey());
						break;
					}
				}
			if(records.add(new ArrayList<Node>(record)))
				countRows++;
			}
		System.out.println("Number of rows in the 1st increaseBitScoreAndGOC query: "+countRows+"/"+addResultsToAlignment(records,aligned,mode)+" of them are added.");		
		
		unmarkAllNodes();
		tx.success(); tx.close();
		} catch(Exception e){
			System.out.println("increaseBitScoreAndGOC1: " + e.getMessage());
		}
		return true;
	} );	
		
	TransactionTemplate template2 = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
	boolean success2 = template2.with(AkkaSystem.graphDb).execute( transaction -> {
		StatementResult result;
		int countRows = 0;
		try ( org.neo4j.driver.v1.Transaction tx = bsgoc.beginTransaction() ){	
		markUnalignedNodes();
		
		result = tx.run("match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)<-[t:SIMILARITY]-(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)<-[s:ALIGNS {alignmentNumber: '"+alignmentNo+"'}]-(o) where (ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"')) and length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" return p,n order by t.similarity desc, length(FILTER(x in p.annotations WHERE x in n.annotations)) desc");
		//System.out.println("Number of records in query: "+result.list().size());
		records.clear();
		aligned.clear();
		while(result.hasNext()){
			Record row = result.next();
			record.clear();
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null)
					switch (column.getKey()) {
					case "p":
						record.add(0,row.get( column.getKey() ).asNode());
						break;
					case "n":
						record.add(1,row.get( column.getKey() ).asNode());
						break;
					default:
						System.out.println("Unexpected column"+column.getKey());
						break;
					}
				}
			if(records.add(new ArrayList<Node>(record)))
				countRows++;
			}
		System.out.println("Number of rows in the 2nd increaseBitScoreAndGOC query: "+countRows+"/"+addResultsToAlignment(records,aligned,mode)+" of them are added.");
		unmarkAllNodes();
		tx.success(); tx.close();
		} catch(Exception e){
			System.out.println("increaseBitScoreAndGOC2: " + e.getMessage());
		}
		return true;
	} );	
		
	TransactionTemplate template3 = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );	
	boolean success3 = template3.with(AkkaSystem.graphDb).execute( transaction -> {
		StatementResult result;
		int countRows = 0;		
		try ( org.neo4j.driver.v1.Transaction tx = bsgoc.beginTransaction() ){	
		markUnalignedNodes();
		
		result = tx.run("match (p:Organism2)<-[t:SIMILARITY]-(n:Organism1) where (ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"')) and length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" return p,n order by t.similarity desc, length(FILTER(x in p.annotations WHERE x in n.annotations)) desc");
		//System.out.println("Number of records in query: "+result.list().size());
		records.clear();
		aligned.clear();
		countRows = 0;
		while(result.hasNext()){
			Record row = result.next();
			record.clear();
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null)
					switch (column.getKey()) {
					case "p":
						record.add(0,row.get( column.getKey() ).asNode());
						break;
					case "n":
						record.add(1,row.get( column.getKey() ).asNode());
						break;
					default:
						System.out.println("Unexpected column"+column.getKey());
						break;
					}
				}
			if(records.add(new ArrayList<Node>(record)))
				countRows++;
			}
		System.out.println("Number of rows in the 3rd increaseBitScoreAndGOC query: "+countRows+"/"+addResultsToAlignment(records,aligned,mode)+" of them are added.");
		unmarkAllNodes();
	tx.success(); tx.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			System.out.println("increaseBitScoreAndGOC3: " + e.getMessage());
		} //finally {bsgoc.close();} // bsgoc.closeAsync();
		return true;
	} );	
	while(bsgoc.isOpen()) {
		if (success && success2 && success3)
			bsgoc.close();
		else
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
	}	
	this.bs = as.calculateGlobalBenchmarks((Aligner)this);	
	//this.bs = as.calculateGlobalBenchmarks(TypedActor.<Aligner>self());
		
}

// Yavas calisiyor. Double Edge Side'dan sonra tek eşleşme geliyor ki 3. sıradaki Single Edge Side eşleşmelere katkısı olsun.
public void increaseGOCAndBitScore(int minCommonAnnotations, char mode){
System.out.println("increaseGOCAndBitScore for Aligner "+this.alignmentNo);	
Session gocbs = AkkaSystem.driver.session();
ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
ArrayList<Node> record = new ArrayList<Node>();
Set<Node> aligned = new HashSet<Node>();

TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );	
boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
	StatementResult result;
	int countRows = 0;
	try ( org.neo4j.driver.v1.Transaction tx = gocbs.beginTransaction() ) {
		
		markUnalignedNodes();

		result = tx.run("match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)<-[t:SIMILARITY]-(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[s:SIMILARITY]->(o) where (ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN o.marked WHERE x = '"+this.alignmentNo+"')) return o,m,p,n order by length(FILTER(x in p.annotations WHERE x in n.annotations))+length(FILTER(x in o.annotations WHERE x in m.annotations)) desc");

		while(result.hasNext()){
			Record row = result.next();
			record.clear();
	for ( Entry<String,Object> column : row.asMap().entrySet() ){
		if(column.getValue()!=null)
			switch (column.getKey()) {
			case "o":
				record.add(0,row.get( column.getKey() ).asNode());
				break;
			case "m":
				record.add(1,row.get( column.getKey() ).asNode());
				break;
			case "p":
				record.add(2,row.get( column.getKey() ).asNode());
				break;
			case "n":
				record.add(3,row.get( column.getKey() ).asNode());
				break;
			default:
				System.out.println("Unexpected column"+column.getKey());
				break;
			}
		}
	if(records.add(new ArrayList<Node>(record)))
		countRows++;
	}
		System.out.println("Number of rows in the 1st increaseGOCAndBitScore query: "+countRows+"/"+addResultsToAlignment(records,aligned,mode)+" of them are added.");

		unmarkAllNodes();
		
		tx.success(); tx.close();
	} catch(Exception e){
	}
	
	return true;});


TransactionTemplate template2 = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );	
boolean success2 = template2.with(AkkaSystem.graphDb).execute( transaction -> {
	StatementResult result;
	int countRows = 0;
	try ( org.neo4j.driver.v1.Transaction tx = gocbs.beginTransaction() ) {
		
		markUnalignedNodes();
		result = tx.run("match (p:Organism2)<-[t:SIMILARITY]-(n:Organism1) where (ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"')) and length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" return p,n order by length(FILTER(x in p.annotations WHERE x in n.annotations)) desc");
		
//System.out.println("Number of records in query: "+result.list().size());
records.clear();
aligned.clear();
countRows = 0;
while(result.hasNext()){
	Record row = result.next();
	record.clear();
	for ( Entry<String,Object> column : row.asMap().entrySet() ){
		if(column.getValue()!=null)
			switch (column.getKey()) {
			case "p":
				record.add(0,row.get( column.getKey() ).asNode());
				break;
			case "n":
				record.add(1,row.get( column.getKey() ).asNode());
				break;
			default:
				System.out.println("Unexpected column"+column.getKey());
				break;
			}
		}
	if(records.add(new ArrayList<Node>(record)))
		countRows++;
	}
System.out.println("Number of rows in the 2nd increaseGOCAndBitScore query: "+countRows+"/"+addResultsToAlignment(records,aligned,mode)+" of them are added.");

	unmarkAllNodes();
		
		tx.success(); tx.close();
	} catch(Exception e){
	}
	return true;});

TransactionTemplate template3 = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );	
boolean success3 = template3.with(AkkaSystem.graphDb).execute( transaction -> {
	StatementResult result;
	int countRows = 0;
	try ( org.neo4j.driver.v1.Transaction tx = gocbs.beginTransaction() ) {
		
		markUnalignedNodes();
		result = tx.run("match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[t:ALIGNS {alignmentNumber: '"+alignmentNo+"'}]->(n:Organism1)-[r:INTERACTS_1]-(m:Organism1) where (ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN o.marked WHERE x = '"+this.alignmentNo+"')) and length(FILTER(x in o.annotations WHERE x in m.annotations)) >="+minCommonAnnotations+" return o,m order by length(FILTER(x in o.annotations WHERE x in m.annotations)) desc");
	//System.out.println("Number of records in query: "+result.list().size());
	records.clear();
	aligned.clear();
	countRows = 0;
	while(result.hasNext()){
		Record row = result.next();
		record.clear();
		for ( Entry<String,Object> column : row.asMap().entrySet() ){
			if(column.getValue()!=null)
				switch (column.getKey()) {
				case "o":
					record.add(0,row.get( column.getKey() ).asNode());
					break;
				case "m":
					record.add(1,row.get( column.getKey() ).asNode());
					break;
				default:
					System.out.println("Unexpected column"+column.getKey());
					break;
				}
			}
		if(records.add(new ArrayList<Node>(record)))
			countRows++;
		}
	System.out.println("Number of rows in the 3rd increaseGOCAndBitScore query: "+countRows+"/"+addResultsToAlignment(records,aligned,mode)+" of them are added.");
		unmarkAllNodes();
		
		tx.success(); tx.close();
	} catch(Exception e){
	}
	
	return true;});
while(gocbs.isOpen()) {
	if (success && success2 && success3)
	{
		gocbs.close();
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}	

	this.bs = as.calculateGlobalBenchmarks((Aligner)this);	
}

public void increaseBitScoreWithTopMappings(int limit, char mode) {
	System.out.println("increaseBitScoreWithTopMappings for Aligner "+this.alignmentNo+" with a limit of "+limit);
	ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
	ArrayList<Node> record = new ArrayList<Node>();
	Set<Node> aligned = new HashSet<Node>();
	Session ibswtm = AkkaSystem.driver.session();
	TransactionTemplate template3 = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );	
	boolean success = template3.with(AkkaSystem.graphDb).execute( transaction -> {
		StatementResult result;
		int countRows = 0;		
		try ( org.neo4j.driver.v1.Transaction tx = ibswtm.beginTransaction() ){	
		markUnalignedNodes();
		result = tx.run("match (p:Organism2)<-[t:SIMILARITY]-(n:Organism1) where (ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"')) return p,n order by t.similarity desc limit "+limit);
		records.clear();
		aligned.clear();
		countRows = 0;
		while(result.hasNext()){
			Record row = result.next();
			record.clear();
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null)
					switch (column.getKey()) {
					case "p":
						record.add(0,row.get( column.getKey() ).asNode());
						break;
					case "n":
						record.add(1,row.get( column.getKey() ).asNode());
						break;
					default:
						System.out.println("Unexpected column"+column.getKey());
						break;
					}
				}
			if(records.add(new ArrayList<Node>(record)))
				countRows++;
			}
		System.out.println("Number of rows in the increaseBitScoreWithTopMappings query: "+countRows+"/"+addResultsToAlignment(records,aligned,mode)+" of them are added.");
		unmarkAllNodes();
		tx.success(); tx.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			System.out.println("increaseBitScoreWithTopMappings: " + e.getMessage());
		} finally {ibswtm.close();} // bsgoc.closeAsync();
		return true;
	} );
	if(!success)
		System.err.println("Method increaseBitScoreWithTopMappings was interrupted");
	
	this.bs = as.calculateGlobalBenchmarks((Aligner)this);	
	
}
// Çok Maliyetli. Sadece işlem sonrasında ya da sonlarda kullanılabilir.
public void increaseGOCWithTopMappings(int limit, char mode) {
	System.out.println("increaseGOCWithTopMappings for Aligner "+this.alignmentNo+" with a limit of "+limit);
	ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
	ArrayList<Node> record = new ArrayList<Node>();
	Set<Node> aligned = new HashSet<Node>();
	Session igocwtm = AkkaSystem.driver.session();
	TransactionTemplate template3 = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );	
	boolean success = template3.with(AkkaSystem.graphDb).execute( transaction -> {
		StatementResult result;
		int countRows = 0;		
		try ( org.neo4j.driver.v1.Transaction tx = igocwtm.beginTransaction() ){	
		markUnalignedNodes();
		result = tx.run("match (p:Organism2),(n:Organism1) where (ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"')) return p,n order by length(FILTER(x in p.annotations WHERE x in n.annotations)) desc limit "+limit);
		records.clear();
		aligned.clear();
		countRows = 0;
		while(result.hasNext()){
			Record row = result.next();
			record.clear();
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null)
					switch (column.getKey()) {
					case "p":
						record.add(0,row.get( column.getKey() ).asNode());
						break;
					case "n":
						record.add(1,row.get( column.getKey() ).asNode());
						break;
					default:
						System.out.println("Unexpected column"+column.getKey());
						break;
					}
				}
			if(records.add(new ArrayList<Node>(record)))
				countRows++;
			}
		System.out.println("Number of rows in the increaseGOCWithTopMappings query: "+countRows+"/"+addResultsToAlignment(records,aligned,mode)+" of them are added.");
		unmarkAllNodes();
	tx.success(); tx.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			System.out.println("increaseGOCWithTopMappings: " + e.getMessage());
		} finally {igocwtm.close();} // bsgoc.closeAsync();
		return true;
	} );
	
	if(!success)
		System.err.println("Method increaseGOCWithTopMappings was interrupted");
	
	this.bs = as.calculateGlobalBenchmarks((Aligner)this);	
	
}


/*
 * Bu metodun hizalaman�n ba�lar�nda �al��mas� daha uygundur. 
 * 
 * */
public void increaseECWithFunctionalParameters(int k1, int k2, double sim1, double sim2, char mode){
	System.out.println("increaseECWithFunctionalParameters for Aligner "+this.alignmentNo);
	
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
	boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
		StatementResult result;
		int countRows = 0;
		Session ecwfp = AkkaSystem.driver.session();
		ecwfp.isOpen();
		try ( org.neo4j.driver.v1.Transaction tx = ecwfp.beginTransaction() ){
			
//			if(AkkaSystem.graphDb.isAvailable(2000))
			markUnalignedNodes();
//			else
//				{tx.failure();tx.close();}
			ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
			ArrayList<Node> record = new ArrayList<Node>();
			if(k2 !=0 || sim2 !=0) {
			result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)<-[t:SIMILARITY]-(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[s:SIMILARITY]->(o) "
					+ "where length(FILTER(x in p.annotations WHERE x in n.annotations)) >= "+k1+" and length(FILTER(x in o.annotations WHERE x in m.annotations)) >= "+k2+" and t.similarity >= "+sim1+" and s.similarity >= "+sim2+" "
					+"and ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN o.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') return o,m,p,n order by t.similarity+s.similarity desc");
//			System.out.println("Number of records in query: "+result.list().size());
			while(result.hasNext()){
				Record row = result.next();
				record.clear();
				for ( Entry<String,Object> column : row.asMap().entrySet() ){
					if(column.getValue()!=null)
						switch (column.getKey()) {
						case "o":
							record.add(0,row.get( column.getKey() ).asNode());
							break;
						case "m":
							record.add(1,row.get( column.getKey() ).asNode());
							break;
						case "p":
							record.add(2,row.get( column.getKey() ).asNode());
							break;
						case "n":
							record.add(3,row.get( column.getKey() ).asNode());
							break;
						default:
							System.out.println("Unexpected column"+column.getKey());
							break;
						}
					}
				records.add(new ArrayList<Node>(record));
				countRows++;
				}
			System.out.println("Number of rows in the increaseECWithFunctionalParameters DOUBLE EDGE SIDE query: "+countRows);
			} 
			
			else {	
				result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)<-[t:SIMILARITY]-(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)<-[a:ALIGNS]-(o) "
						+ "where length(FILTER(x in p.annotations WHERE x in n.annotations)) >= "+k1+" and t.similarity >= "+sim1+" "
						+"and ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') return p,n order by t.similarity desc");
//				System.out.println("Number of records in query: "+result.list().size());
				while(result.hasNext()){
					Record row = result.next();
					record.clear();
					for ( Entry<String,Object> column : row.asMap().entrySet() ){
						if(column.getValue()!=null)
							switch (column.getKey()) {
							case "p":
								record.add(0,row.get( column.getKey() ).asNode());
								break;
							case "n":
								record.add(1,row.get( column.getKey() ).asNode());
								break;
							default:
								System.out.println("Unexpected column"+column.getKey());
								break;
							}
						}
					records.add(new ArrayList<Node>(record));
					countRows++;
				}
				System.out.println("Number of rows in the increaseECWithFunctionalParameters SINGLE EDGE SIDE query: "+countRows);
			}
			
			Set<Node> aligned = new HashSet<Node>();
			System.out.println(addResultsToAlignment(records,aligned,mode)+" of the rows of increaseECWithFunctionalParameter query were added.");
			
//			if(AkkaSystem.graphDb.isAvailable(2000))
				unmarkAllNodes();
//			else
//				{tx.failure();tx.close();}
			tx.success(); tx.close();
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			System.out.println("increaseECWithFunctionalParameters: " + e1.getMessage());
		} finally {ecwfp.close();}
		return true;
	} );
	
	if(!success)
		System.err.println("Method increaseECWithFunctionalParameters was interrupted");
	
	this.bs = as.calculateGlobalBenchmarks((Aligner)this);	
}

public void increaseFunctionalParametersWithPower(int minCommonAnnotations, double sim,int power, char powerMode, char mode) {
	
	System.out.println("increaseFunctionalParametersWithPower2 for Aligner "+this.alignmentNo);	
	Session gocbs = AkkaSystem.driver.session();
	ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
	ArrayList<Node> record = new ArrayList<Node>();
	Set<Node> aligned = new HashSet<Node>();
	boolean success = false;
	
	if(sim >0) {
		
		TransactionTemplate template2 = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );	
		success = template2.with(AkkaSystem.graphDb).execute( transaction -> {
			StatementResult result;
			int countRows = 0;
			try ( org.neo4j.driver.v1.Transaction tx = gocbs.beginTransaction() ) {
				
				markUnalignedNodes();
				
				if(powerMode == '4' || powerMode == '3' )
					result = tx.run("match (p:Organism2)<-[t:SIMILARITY]-(n:Organism1) where (ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"')) and length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" and t.similarity >= "+sim+" and p.power"+powerMode+" >= "+power+" and n.power"+powerMode+" >= "+power+" return p,n order by length(FILTER(x in p.annotations WHERE x in n.annotations)) desc, t.similarity desc");
				else
					result = tx.run("match (p:Organism2)<-[t:SIMILARITY]-(n:Organism1) where (ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"')) and length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" and t.similarity >= "+sim+" and p.power2 >= "+power+" and n.power2 >= "+power+" return p,n order by length(FILTER(x in p.annotations WHERE x in n.annotations)) desc, t.similarity desc");
				
		//System.out.println("Number of records in query: "+result.list().size());
		records.clear();
		aligned.clear();
		countRows = 0;
		while(result.hasNext()){
			Record row = result.next();
			record.clear();
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null)
					switch (column.getKey()) {
					case "p":
						record.add(0,row.get( column.getKey() ).asNode());
						break;
					case "n":
						record.add(1,row.get( column.getKey() ).asNode());
						break;
					default:
						System.out.println("Unexpected column"+column.getKey());
						break;
					}
				}
			if(records.add(new ArrayList<Node>(record)))
				countRows++;
			}
		System.out.println("increaseFunctionalParametersWithPower2 query with similarity: "+sim+", and minCommonAnnotations: "+minCommonAnnotations+" returned "+countRows+" rows of which "+addResultsToAlignment(records,aligned,mode)+" are added.");
			unmarkAllNodes();
				
				tx.success(); tx.close();
			} catch(Exception e){
			}
			return true;});
		
		
	} else {
		
		TransactionTemplate template2 = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );	
		success = template2.with(AkkaSystem.graphDb).execute( transaction -> {
			StatementResult result;
			int countRows = 0;
			try ( org.neo4j.driver.v1.Transaction tx = gocbs.beginTransaction() ) {
				
				markUnalignedNodes();
				
				if(powerMode == '4' || powerMode == '3' )
					result = tx.run("match (p:Organism2), (n:Organism1) where (ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"')) and length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" and p.power"+powerMode+" >= "+power+" and n.power"+powerMode+" >= "+power+" return p,n,min(p.power"+powerMode+" ,n.power"+powerMode+") order by length(FILTER(x in p.annotations WHERE x in n.annotations)) desc,min(p.power"+powerMode+" ,n.power"+powerMode+") desc");
				else
					result = tx.run("match (p:Organism2), (n:Organism1) where (ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"')) and length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" and p.power2 >= "+power+" and n.power2 >= "+power+" return p,n,min(p.power2 ,n.power2) order by length(FILTER(x in p.annotations WHERE x in n.annotations)) desc,min(p.power2 ,n.power2) desc");
				
		//System.out.println("Number of records in query: "+result.list().size());
		records.clear();
		aligned.clear();
		countRows = 0;
		while(result.hasNext()){
			Record row = result.next();
			record.clear();
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null)
					switch (column.getKey()) {
					case "p":
						record.add(0,row.get( column.getKey() ).asNode());
						break;
					case "n":
						record.add(1,row.get( column.getKey() ).asNode());
						break;
					case "min(p.power2 ,n.power2)":
						;
						break;
					default:
						System.out.println("Unexpected column"+column.getKey());
						break;
					}
				}
			if(records.add(new ArrayList<Node>(record)))
				countRows++;
			}
		System.out.println("increaseFunctionalParametersWithPower2 query without similarity and with minCommonAnnotations: "+minCommonAnnotations+" returned "+countRows+" rows of which "+addResultsToAlignment(records,aligned,mode)+" are added.");
			unmarkAllNodes();
				
				tx.success(); tx.close();
			} catch(Exception e){
				System.out.println("increaseFunctionalParametersWithPower2: " + e.getMessage());
			}
			return true;});
		
	}
	
	if(!success)
		System.err.println("Method increaseFunctionalParametersWithPower2 was interrupted");
	
	this.bs = as.calculateGlobalBenchmarks((Aligner)this);
}

//hizalanmış kenarları artırmadığı görülmüş olaydır. Buyuk olasilikla dugum eslesmeleri yanlisti ve duzeltildi. hala sorgu iyilestirmesi gerekiyor.
// SCC gibi graph algo küme partitionları ile iyileştirilecek.

public void increaseConnectedEdges(int limit, int minCommonAnnotations, boolean doubleOrTriple, char mode){
	System.out.println("increaseConnectedEdges for Aligner "+this.alignmentNo);

	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
	boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
		
		StatementResult result;
		int countRows = 0;
		Session ice = AkkaSystem.driver.session();
		ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
		ArrayList<Node> record = new ArrayList<Node>();
		Set<Node> aligned = new HashSet<Node>();
		
		try ( org.neo4j.driver.v1.Transaction tx = ice.beginTransaction())
	    {
			markUnalignedNodes();
			
//			result = tx.run("match (o:Organism2)-[i2:INTERACTS_2]-(n:Organism2)-[r:ALIGNS]->(m:Organism1)-[i1:INTERACTS_1]-(l:Organism1) where n.marked = false and m.marked = false and o.marked = true and l.marked = true "
//			+ "and not (o)-[:ALIGNS]->(l) return o,l");
			//+ "create (o)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: toString(o.proteinName)+'*'+'"+alignmentNo+"'+'*'+toString(l.proteinName), markedQuery: []}]->(l) set o.marked = true, l.marked = true");
			//on create set o.marked = true, l.marked = true
			//HIZLI match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1) return o,m,p,n limit 500
			if(doubleOrTriple)
				result = tx.run("match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2) match (n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1) where length(FILTER(x in o.annotations WHERE x in n.annotations)) >= "+minCommonAnnotations+" and (ANY(x IN o.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"')) and (ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"')) and (ANY(x IN q.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN l.marked WHERE x = '"+this.alignmentNo+"')) return distinct o,n,p,m,q,l limit "+limit);
			else
				result = tx.run("match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2)-[u3:INTERACTS_2]-(o) match (n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1)-[r3:INTERACTS_1]-(n) where length(FILTER(x in o.annotations WHERE x in n.annotations)) >= "+minCommonAnnotations+" and ANY(x IN o.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN q.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN l.marked WHERE x = '"+this.alignmentNo+"') return distinct o,n,p,m,q,l limit "+limit);
//			result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2)<-[s1:SIMILARITY]-(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1)-[s2:SIMILARITY]->(o) "
//			+ "where o.marked=true and p.marked=true and q.marked=true and n.marked=true and m.marked=true and l.marked=true return o,p,q,n,m,l limit 1000");
			while(result.hasNext()){
				Record row = result.next();
				record.clear();
				for ( Entry<String,Object> column : row.asMap().entrySet() ){
					if(column.getValue()!=null)
						switch (column.getKey()) {
						case "o":
							record.add(0,row.get( column.getKey() ).asNode());
							break;
						case "n":
							record.add(1,row.get( column.getKey() ).asNode());
							break;
						case "p":
							record.add(2,row.get( column.getKey() ).asNode());
							break;
						case "m":
							record.add(3,row.get( column.getKey() ).asNode());
							break;
						case "q":
							record.add(4,row.get( column.getKey() ).asNode());
							break;
						case "l":
							record.add(5,row.get( column.getKey() ).asNode());
							break;
						default:
							System.out.println("Unexpected column"+column.getKey());
							break;
						}
					}
				records.add(new ArrayList<Node>(record));
				countRows++;
				}
			System.out.print("Number of rows in increaseConnectedEdges query1: "+countRows);
			System.out.println(" of which "+addResultsToAlignment(records,aligned,mode)+ " are added.");
			unmarkAllNodes();
			markUnalignedNodes();

	//3 l� klik	
			if(doubleOrTriple)
				result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1) where ANY(x IN o.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN q.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN l.marked WHERE x = '"+this.alignmentNo+"') return o,n,p,m,q,l limit "+limit);
			else
				result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2)-[u3:INTERACTS_2]-(o),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1)-[r3:INTERACTS_1]-(n) where ANY(x IN o.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN q.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN l.marked WHERE x = '"+this.alignmentNo+"') return o,n,p,m,q,l limit "+limit);
//			System.out.println("Number of records in query: "+result.list().size());
			records.clear();
			aligned.clear();
			countRows = 0;
			while(result.hasNext()){
				Record row = result.next();
				record.clear();
				for ( Entry<String,Object> column : row.asMap().entrySet() ){
					if(column.getValue()!=null)
						switch (column.getKey()) {
						case "o":
							record.add(0,row.get( column.getKey() ).asNode());
							break;
						case "n":
							record.add(1,row.get( column.getKey() ).asNode());
							break;
						case "p":
							record.add(2,row.get( column.getKey() ).asNode());
							break;
						case "m":
							record.add(3,row.get( column.getKey() ).asNode());
							break;
						case "q":
							record.add(4,row.get( column.getKey() ).asNode());
							break;
						case "l":
							record.add(5,row.get( column.getKey() ).asNode());
							break;
						default:
							System.out.println("Unexpected column"+column.getKey());
							break;
						}
					}
				records.add(new ArrayList<Node>(record));
				countRows++;
				}
			System.out.print("Number of rows in increaseConnectedEdges query2: "+countRows);
			System.out.println(" of which "+addResultsToAlignment(records,aligned,mode)+ " are added.");
			unmarkAllNodes();
			markUnalignedNodes();
	//3 l� klik	
			if(doubleOrTriple)
				result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1) where ANY(x IN o.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and NOT ANY(x IN q.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') and NOT ANY(x IN l.marked WHERE x = '"+this.alignmentNo+"') return o,n,p,m limit "+limit);
			else
				result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2)-[u3:INTERACTS_2]-(o),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1)-[r3:INTERACTS_1]-(n) where ANY(x IN o.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and NOT ANY(x IN q.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') and NOT ANY(x IN l.marked WHERE x = '"+this.alignmentNo+"') return o,n,p,m limit "+limit);
//			System.out.println("Number of records in query: "+result.list().size());
			records.clear();
			aligned.clear();
			countRows = 0;
			while(result.hasNext()){
				Record row = result.next();
				record.clear();
				for ( Entry<String,Object> column : row.asMap().entrySet() ){
					if(column.getValue()!=null)
						switch (column.getKey()) {
						case "o":
							record.add(0,row.get( column.getKey() ).asNode());
							break;
						case "n":
							record.add(1,row.get( column.getKey() ).asNode());
							break;
						case "p":
							record.add(2,row.get( column.getKey() ).asNode());
							break;
						case "m":
							record.add(3,row.get( column.getKey() ).asNode());
							break;
						default:
							System.out.println("Unexpected column"+column.getKey());
							break;
						}
					}
				records.add(new ArrayList<Node>(record));
				countRows++;
				}
			System.out.print("Number of rows in increaseConnectedEdges query3: "+countRows);
			System.out.println(" of which "+addResultsToAlignment(records,aligned,mode)+ " are added.");
			unmarkAllNodes();
			markUnalignedNodes();
	//3 l� klik
			if(doubleOrTriple)
				result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1) where ANY(x IN o.marked WHERE x = '"+this.alignmentNo+"') and NOT ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and NOT ANY(x IN q.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and NOT ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') and NOT ANY(x IN l.marked WHERE x = '"+this.alignmentNo+"') return o,n limit "+limit);
			else
				result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2)-[u3:INTERACTS_2]-(o),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1)-[r3:INTERACTS_1]-(n) where ANY(x IN o.marked WHERE x = '"+this.alignmentNo+"') and NOT ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and NOT ANY(x IN q.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and NOT ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') and NOT ANY(x IN l.marked WHERE x = '"+this.alignmentNo+"') return o,n limit "+limit);
//			System.out.println("Number of records in query: "+result.list().size());
			records.clear();
			aligned.clear();
			countRows = 0;
			while(result.hasNext()){
				Record row = result.next();
				record.clear();
				for ( Entry<String,Object> column : row.asMap().entrySet() ){
					if(column.getValue()!=null)
						switch (column.getKey()) {
						case "o":
							record.add(0,row.get( column.getKey() ).asNode());
							break;
						case "n":
							record.add(1,row.get( column.getKey() ).asNode());
							break;
						default:
							System.out.println("Unexpected column"+column.getKey());
							break;
						}
					}
				records.add(new ArrayList<Node>(record));
				countRows++;
				}
			System.out.print("Number of rows in increaseConnectedEdges query4: "+countRows);
			System.out.println(" of which "+addResultsToAlignment(records,aligned,mode)+ " are added.");
			unmarkAllNodes();
			// 4 l� klik sorgusu
			//tx.run("match (a:Organism1)-[r1:INTERACTS_1]-(b:Organism1)-[r2:INTERACTS_1]-(c:Organism1)-[r3:INTERACTS_1]-(a:Organism1),(c:Organism1)-[r4:INTERACTS_1]-(d:Organism1)-[r5:INTERACTS_1]-(a:Organism1),(d:Organism1)-[r6:INTERACTS_1]-(b:Organism1) return a,b,c,d limit 50");
			//tx.run("match (e:Organism2)-[r7:INTERACTS_2]-(f:Organism2)-[r8:INTERACTS_2]-(g:Organism2)-[r9:INTERACTS_2]-(e:Organism2),(g:Organism2)-[r10:INTERACTS_2]-(h:Organism2)-[r11:INTERACTS_2]-(e:Organism2),(h:Organism2)-[r12:INTERACTS_2]-(f:Organism2) return e,f,g,h limit 50");
			//tx.run("match (a:Organism1)-[r1:INTERACTS_1]-(b:Organism1)-[r2:INTERACTS_1]-(c:Organism1)-[r3:INTERACTS_1]-(a:Organism1),(c:Organism1)-[r4:INTERACTS_1]-(d:Organism1)-[r5:INTERACTS_1]-(a:Organism1),(d:Organism1)-[r6:INTERACTS_1]-(b:Organism1) match (e:Organism2)-[r7:INTERACTS_2]-(f:Organism2)-[r8:INTERACTS_2]-(g:Organism2)-[r9:INTERACTS_2]-(e:Organism2),(g:Organism2)-[r10:INTERACTS_2]-(h:Organism2)-[r11:INTERACTS_2]-(e:Organism2),(h:Organism2)-[r12:INTERACTS_2]-(f:Organism2) return a,b,c,d,e,f,g,h limit 1000");	
			tx.success(); tx.close();
	    } catch (Exception e){
	  	  e.printStackTrace();
	    }		
	return true;
} );
	
//	TransactionTemplate template1 = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
//	boolean success1 = template1.with(AkkaSystem.graphDb).execute( transaction -> {	
//	return true;
//} );
	
	if(!success)
		System.err.println("Method increaseConnectedEdges was interrupted");
	
	this.bs = as.calculateGlobalBenchmarks((Aligner)this);	
}

/*
 * Eski Yorum: increaseGOCScore ile hizalanan d���mlerin kom�usu olan d���mlerin de birbiriyle hizalanmas� ile EC art�r�l�r. Kom�u d���mler i�inde �ncelikle GOC ve BitScoreSimilarity art�ranlar tercih edilir.
 * Yeni Yorum: Hizal� d���mlerin kom�usu olan d���mlerin de birbiriyle hizalanmas� ile EC art�r�l�r.
 * 
 * 
 * T�m D��er Metotlarla ilgili: �u y�ntem yanl�� if (aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1))&&aligned.add(records.get(i).get(2))&&aligned.add(records.get(i).get(3)))
 * 
 *  Bu metot kenar i�ermeyen e�le�melerin say�s�n�n �ok olmas� durumunda daha �ok i�e yarayacakt�r.
 *  
 *  
 *  Bu hizalamaya has olabilmesi için sorguda aşağıdaki kısımlar yeniden düzenlenebilir. !!!! 
 *  where r.alignmentNumber = '"+ alignmentNo +"' 
 *  not (o)-[:ALIGNS {alignmentNumber: '+"alignmentNo"+'}]->(l) return o,l
 * */

public void increaseECByAddingPair(int minCommonAnnotations, double sim, char mode){
	System.out.println("increaseECByAddingPair for Aligner "+this.alignmentNo+" with "+minCommonAnnotations+" minCommonAnnotations and "+sim+" similarity");
	TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
	tm.failure(new Throwable("Herkesin tuttuğu kendine"));
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
	boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
		StatementResult result;
		Session ieca = AkkaSystem.driver.session();
		try ( org.neo4j.driver.v1.Transaction tx = ieca.beginTransaction())
	    {
			if(sim > 0) {
			markUnalignedNodes();
			ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
			ArrayList<Node> record = new ArrayList<Node>();
			result = tx.run("match (o:Organism2)-[i2:INTERACTS_2]-(n:Organism2)-[r:ALIGNS {alignmentNumber: '"+alignmentNo+"'}]->(m:Organism1)-[i1:INTERACTS_1]-(l:Organism1)-[s:SIMILARITY]->(o) where NOT ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and NOT ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN o.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN l.marked WHERE x = '"+this.alignmentNo+"') "
			+ "and length(FILTER(x in o.annotations WHERE x in l.annotations)) >= "+minCommonAnnotations+" and s.similarity >="+sim+" and not (o)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"'}]->(l) return o,l");
			//+ "create (o)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: toString(o.proteinName)+'*'+'"+alignmentNo+"'+'*'+toString(l.proteinName), markedQuery: []}]->(l) set o.marked = true, l.marked = true");
			//on create set o.marked = true, l.marked = true
			
			while(result.hasNext()){
				Record row = result.next();
				record.clear();
				for ( Entry<String,Object> column : row.asMap().entrySet() ){
					if(column.getValue()!=null)
						switch (column.getKey()) {
						case "o":
							record.add(0,row.get( column.getKey() ).asNode());
							break;
						case "l":
							record.add(1,row.get( column.getKey() ).asNode());
							break;
						default:
							System.out.println("Unexpected column"+column.getKey());
							break;
						}
					}
				records.add(new ArrayList<Node>(record));
				}
			
			Set<Node> aligned = new HashSet<Node>();
			addResultsToAlignment(records,aligned,mode);
			unmarkAllNodes();
			} else {
				markUnalignedNodes();
				ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
				ArrayList<Node> record = new ArrayList<Node>();
				result = tx.run("match (o:Organism2)-[i2:INTERACTS_2]-(n:Organism2)-[r:ALIGNS {alignmentNumber: '"+alignmentNo+"'}]->(m:Organism1)-[i1:INTERACTS_1]-(l:Organism1) where NOT ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and NOT ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN o.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN l.marked WHERE x = '"+this.alignmentNo+"') "
				+ "and length(FILTER(x in o.annotations WHERE x in l.annotations)) >= "+minCommonAnnotations+" and not (o)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"'}]->(l) return o,l");
				//+ "create (o)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: toString(o.proteinName)+'*'+'"+alignmentNo+"'+'*'+toString(l.proteinName), markedQuery: []}]->(l) set o.marked = true, l.marked = true");
				//on create set o.marked = true, l.marked = true
				
				while(result.hasNext()){
					Record row = result.next();
					record.clear();
					for ( Entry<String,Object> column : row.asMap().entrySet() ){
						if(column.getValue()!=null)
							switch (column.getKey()) {
							case "o":
								record.add(0,row.get( column.getKey() ).asNode());
								break;
							case "l":
								record.add(1,row.get( column.getKey() ).asNode());
								break;
							default:
								System.out.println("Unexpected column"+column.getKey());
								break;
							}
						}
					records.add(new ArrayList<Node>(record));
					}
				
				Set<Node> aligned = new HashSet<Node>();
				
				System.out.println(addResultsToAlignment(records,aligned,mode)+" records were added with increaseECByAddingPair of Aligner "+this.alignmentNo);
				unmarkAllNodes();
			}
			tx.success(); tx.close();
	    } catch (Exception e){
	    	System.out.println("increaseECByAddingPair: " + e.getMessage());
	    } finally {ieca.close();}
		this.bs = as.calculateGlobalBenchmarks(this);
		return true;
	} );
	
	if(!success)
		System.err.println("Method increaseECByAddingPair was interrupted");
	
	this.bs = as.calculateGlobalBenchmarks((Aligner)this);	
}
// Merkezi düğüm bulmak için kullanılırsa işlevsel ölçütler düşük kalır. Test edilmedi. Pek işe yaramıyor gibi.
public void increaseCentralEdges(int power2, int power3, int power4, char mode) {
	
	System.out.println("increaseGOCWithTopMappings for Aligner "+this.alignmentNo);	
	Session igcwtm = AkkaSystem.driver.session();
	ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
	ArrayList<Node> record = new ArrayList<Node>();
	Set<Node> aligned = new HashSet<Node>();
	
	TransactionTemplate template1 = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );	
	boolean success = template1.with(AkkaSystem.graphDb).execute( transaction -> {
		StatementResult result;
		int countRows = 0;
		try ( org.neo4j.driver.v1.Transaction tx = igcwtm.beginTransaction() ) {
			
			markUnalignedNodes();
			result = tx.run("match (p1:Organism2)-[i:INTERACTS_2]-(p:Organism2), (n:Organism1)-[j:INTERACTS_1]-(n1:Organism1) where "
					+ "(ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"')) "
							+ "and (ANY(x IN p1.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n1.marked WHERE x = '"+this.alignmentNo+"')) "
							+ "and p1.power2 >="+power2+" and p.power2 >="+power2+" and p1.power3 >="+power3+" and p.power3 >="+power3+" and p1.power4 >="+power4+" and p.power4 >= "+power4+" "
							+ "and n1.power2 >="+power2+" and n.power2 >="+power2+" and n1.power3 >="+power3+" and n.power3 >="+power3+" and n1.power4 >="+power4+" and n.power4 >= "+power4+" "
									+ "return p,n,p1,n1 order by length(FILTER(x in p.annotations WHERE x in n.annotations))+length(FILTER(x in p1.annotations WHERE x in n1.annotations)) desc");
			
	//System.out.println("Number of records in query: "+result.list().size());
	records.clear();
	aligned.clear();
	countRows = 0;
	while(result.hasNext()){
		Record row = result.next();
		record.clear();
		for ( Entry<String,Object> column : row.asMap().entrySet() ){
			if(column.getValue()!=null)
				switch (column.getKey()) {
				case "p":
					record.add(0,row.get( column.getKey() ).asNode());
					break;
				case "n":
					record.add(1,row.get( column.getKey() ).asNode());
					break;
				case "p1":
					record.add(2,row.get( column.getKey() ).asNode());
					break;
				case "n1":
					record.add(3,row.get( column.getKey() ).asNode());
					break;
				default:
					System.out.println("Unexpected column"+column.getKey());
					break;
				}
			}
		if(records.add(new ArrayList<Node>(record)))
			countRows++;
		}
		System.out.println("increaseCentralEdges BOTH EDGES query returned "+countRows+" rows of which "+addResultsToAlignment(records,aligned,mode)+" are added.");
		unmarkAllNodes();
			
			tx.success(); tx.close();
		} catch(Exception e){
			System.out.println("increaseCentralEdges: " + e.getMessage());
		} // finally {igcwtm.close();}
		return true;});
	
	TransactionTemplate template2 = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );	
	boolean success2 = template2.with(AkkaSystem.graphDb).execute( transaction -> {
		StatementResult result;
		int countRows = 0;
		try ( org.neo4j.driver.v1.Transaction tx = igcwtm.beginTransaction() ) {
			
			markUnalignedNodes();
			result = tx.run("match (p1:Organism2)-[i:INTERACTS_2]-(p:Organism2)-[a:ALIGNS]-> (n:Organism1)-[j:INTERACTS_1]-(n1:Organism1) where "
					+ "not (ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and not ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"')) "
							+ "and (ANY(x IN p1.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n1.marked WHERE x = '"+this.alignmentNo+"')) "
							+ "and p1.power2 >="+power2+" and p1.power3 >="+power3+" and p1.power4 >="+power4+" "
							+ "and n1.power2 >="+power2+" and n1.power3 >="+power3+" and n1.power4 >="+power4+" "
									+ "return p1,n1 order by length(FILTER(x in p1.annotations WHERE x in n1.annotations)) desc");
			
	//System.out.println("Number of records in query: "+result.list().size());
	records.clear();
	aligned.clear();
	countRows = 0;
	while(result.hasNext()){
		Record row = result.next();
		record.clear();
		for ( Entry<String,Object> column : row.asMap().entrySet() ){
			if(column.getValue()!=null)
				switch (column.getKey()) {
				case "p1":
					record.add(0,row.get( column.getKey() ).asNode());
					break;
				case "n1":
					record.add(1,row.get( column.getKey() ).asNode());
					break;
				default:
					System.out.println("Unexpected column"+column.getKey());
					break;
				}
			}
		if(records.add(new ArrayList<Node>(record)))
			countRows++;
		}
		System.out.println("increaseCentralEdges SINGLE EDGE query returned "+countRows+" rows of which "+addResultsToAlignment(records,aligned,mode)+" are added.");
		unmarkAllNodes();

			
			tx.success(); tx.close();
		} catch(Exception e){
			System.out.println("increaseCentralEdges: " + e.getMessage());
		} finally {igcwtm.close();}
		return true;});
	
	while(igcwtm.isOpen()) {
		if (success && success2)
		{
			igcwtm.close();
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
this.bs = as.calculateGlobalBenchmarks((Aligner)this);
}

// Sıralı olması gerekiyordu!!! Sıralı oldu ama bitmedi.
public Future<Boolean> alignCentralPowerNodes(int minCommonAnnotations, double sim, int power2, int power3, int power4, char mode){
	System.out.println("Align Central Power Nodes for Aligner "+this.alignmentNo);
	
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
	boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
		StatementResult result;
		int countRows = 0;
		Session acn = AkkaSystem.driver.session();
		try ( org.neo4j.driver.v1.Transaction tx = acn.beginTransaction() ){		
		markUnalignedNodes();
		ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
		ArrayList<Node> record = new ArrayList<Node>();
		if (sim>0.0)
			result = tx.run("match (p:Organism2)<-[t:SIMILARITY]-(n:Organism1) where (ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"')) and length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" and t.similarity >= "+sim+" and p.power2 >= "+power2+" and n.power2 >= "+power2+" and p.power3 >= "+power3+" and n.power3 >= "+power3+" and p.power4 >= "+power4+" and n.power4 >= "+power4+" return p,n,min(n.power4, p.power4) ,min(n.power3, p.power3) ,min(n.power2, p.power2) order by min(n.power4, p.power4) desc, min(n.power3, p.power3) desc,min(n.power2,p.power2) desc");
		else
			result = tx.run("match (p:Organism2),(n:Organism1) where (ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"')) and length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" and p.power2 >= "+power2+" and n.power2 >= "+power2+" and p.power3 >= "+power3+" and n.power3 >= "+power3+" and p.power4 >= "+power4+" and n.power4 >= "+power4+" return p,n,min(n.power4, p.power4) ,min(n.power3, p.power3) ,min(n.power2, p.power2)  order by min(n.power4, p.power4) desc, min(n.power3, p.power3) desc,min(n.power2,p.power2) desc");
		while(result.hasNext()){
			Record row = result.next();
			record.clear();
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null)
					switch (column.getKey()) {
					case "p":
						record.add(0,row.get( column.getKey() ).asNode());
						break;
					case "n":
						record.add(1,row.get( column.getKey() ).asNode());
						break;
					case "min(n.power4, p.power4)":
						;
						break;
					case "min(n.power3, p.power3)":
						;
						break;
					case "min(n.power2, p.power2)":
						;
						break;
					default:
						System.out.println("Unexpected column "+column.getKey());
						break;
					}
				}
			records.add(new ArrayList<Node>(record));
			countRows++;
			}
		Set<Node> aligned = new HashSet<Node>();
		System.out.println("Number of rows in alignCentralPowerNodes query:"+countRows+" of "+addResultsToAlignment(records,aligned,mode)+" were added.");
		
		unmarkAllNodes();
			tx.success(); tx.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			System.out.println("alignCentralNodes: " + e.getMessage());
			return false;
		} finally {acn.close();}
		return true;
	} );

	this.bs = as.calculateGlobalBenchmarks((Aligner)this);	
	if(success)
		return Futures.successful(true);
	else
		return Futures.successful(false);
}
// denenmedi
// algorithms: betweenness, harmonic, pagerank and closeness (not working),
public Future<Boolean> alignAlternativeCentralNodes(int minCommonAnnotations, double sim, double score1, double score2, String algorithm, char mode){
System.out.println("Align Central "+algorithm+" Nodes for Aligner "+this.alignmentNo);
	
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS );
	boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
		StatementResult result;
		int countRows = 0;
		Session acn = AkkaSystem.driver.session();
		try ( org.neo4j.driver.v1.Transaction tx = acn.beginTransaction() ){		
		markUnalignedNodes();
		
		ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
		ArrayList<Node> record = new ArrayList<Node>();
		if(sim>0.0)
			result = tx.run("match (p:Organism2)<-[t:SIMILARITY]-(n:Organism1) where (ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"')"
				+ " and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"')) and length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations
				+ " and t.similarity >= "+sim 
				+ " and p."+algorithm+" >"+score2+" and n."+algorithm+" >"+score1		
				+ " return p,n,min(p."+algorithm+", n."+algorithm+") order by min(p."+algorithm+", n."+algorithm+"), length(FILTER(x in p.annotations WHERE x in n.annotations)) desc");
		else
			result = tx.run("match (p:Organism2),(n:Organism1) where (ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"')"
					+ " and ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"')) and length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations
					+ " and p."+algorithm+" >"+score2+" and n."+algorithm+" >"+score1		
					+ " return p,n,min(p."+algorithm+", n."+algorithm+") order by min(p."+algorithm+", n."+algorithm+"), length(FILTER(x in p.annotations WHERE x in n.annotations)) desc");
		while(result.hasNext()){
			Record row = result.next();
			record.clear();
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null)
					switch (column.getKey()) {
					case "p":
						record.add(0,row.get( column.getKey() ).asNode());
						break;
					case "n":
						record.add(1,row.get( column.getKey() ).asNode());
						break;
					case "min(p.pagerank, n.pagerank)":
						;
						break;
					case "min(p.betweenness, n.betweenness)":
						;
						break;
					case "min(p.closeness, n.closeness)":
						;
						break;
					case "min(p.harmonic, n.harmonic)":
						;
						break;
					default:
						System.out.println("Unexpected column "+column.getKey());
						break;
					}
				}
			records.add(new ArrayList<Node>(record));
			countRows++;
			}
		Set<Node> aligned = new HashSet<Node>();
		System.out.println("Number of rows in alignAlternativeCentralNodes query: "+countRows+" of "+addResultsToAlignment(records,aligned,mode)+" were added.");
		unmarkAllNodes();
			tx.success(); tx.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			System.out.println("alignAlternativeCentralNodes: " + e.getMessage());
			return false;
		} finally {acn.close();}
		return true;
	} );

	this.bs = as.calculateGlobalBenchmarks((Aligner)this);	
	if(success)
		return Futures.successful(true);
	else
		return Futures.successful(false);
}

// Dosyadan okutulmayacak. AkkaSystemdeki ArrayListten okutulacak. Alttaki metot için de geçerli.

public void alignClusters(int minCommonAnnotations, double sim, String clusterType, long clusterIDOfOrganism1, long clusterIDOfOrganism2, char mode) {
	System.out.println("alignClusters for Aligner "+this.alignmentNo);
	TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
	tm.failure(new Throwable("Herkesin tuttuğu kendine"));
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
	boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
		StatementResult result;
		Session ieca = AkkaSystem.driver.session();
		try ( org.neo4j.driver.v1.Transaction tx = ieca.beginTransaction())
	    {
				markUnalignedNodes();
				ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
				ArrayList<Node> record = new ArrayList<Node>();
				if(sim>0.0)
					result = tx.run("match (n:Organism2 {"+clusterType+": "+clusterIDOfOrganism2+"})<-[s:SIMILARITY]-(m:Organism1 {"+clusterType+": "+clusterIDOfOrganism1+"}) where ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') "
							+ "and length(FILTER(x in n.annotations WHERE x in m.annotations)) >= "+minCommonAnnotations+" and s.similarity >= "+sim+" return n,m");
				else
					result = tx.run("match (n:Organism2 {"+clusterType+": "+clusterIDOfOrganism2+"}),(m:Organism1 {"+clusterType+": "+clusterIDOfOrganism1+"}) where ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') "
							+ "and length(FILTER(x in n.annotations WHERE x in m.annotations)) >= "+minCommonAnnotations+" return n,m");
				while(result.hasNext()){
					Record row = result.next();
					record.clear();
					for ( Entry<String,Object> column : row.asMap().entrySet() ){
						if(column.getValue()!=null)
							switch (column.getKey()) {
							case "n":
								record.add(0,row.get( column.getKey() ).asNode());
								break;
							case "m":
								record.add(1,row.get( column.getKey() ).asNode());
								break;
							default:
								System.out.println("Unexpected column"+column.getKey());
								break;
							}
						}
					records.add(new ArrayList<Node>(record));
					}
				
				Set<Node> aligned = new HashSet<Node>();
				System.out.println(addResultsToAlignment(records,aligned,mode)+" mappings were added with alignClusters Method.");
				unmarkAllNodes();	

			tx.success(); tx.close();
	    } catch (Exception e){
	    	System.out.println("alignClusters: " + e.getMessage());
	    } finally {ieca.close();}
		this.bs = as.calculateGlobalBenchmarks((Aligner)this);	
		return true;
	} );	
	if(!success)
		System.err.println("Method alignClusters was interrupted.");
}

/**
 * addPair true yapılarak diğer ucu birbirine hizalanmış düğümler birbirine hizalanır.
 * Treshold değeri kümelerin birbirine eşlenebilirlik oranıdır.
 * 
 * */

public void alignClusterEdges(int minCommonAnnotations, String clusterType,long clusterIDOfOrganism1, long clusterIDOfOrganism2,boolean addPair, char mode) {
	
	System.out.println("alignClusterEdges for Aligner "+this.alignmentNo);
	TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
	tm.failure(new Throwable("Herkesin tuttuğu kendine"));
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
	boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
		StatementResult result;
		Session ieca = AkkaSystem.driver.session();
		try ( org.neo4j.driver.v1.Transaction tx = ieca.beginTransaction())
	    {
				markUnalignedNodes();
				ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
				ArrayList<Node> record = new ArrayList<Node>();
				if(addPair) {
					result = tx.run("match (n:Organism2 {"+clusterType+": "+clusterIDOfOrganism2+"})<-[s:SIMILARITY]-(m:Organism1 {"+clusterType+": "+clusterIDOfOrganism1+"})"
							+ "-[i1:INTERACTS_1]-(l:Organism1 {"+clusterType+": "+clusterIDOfOrganism1+"})-[s2:SIMILARITY]->(o:Organism2 {"+clusterType+": "+clusterIDOfOrganism2+"})-[i2:INTERACTS_2]-(n) "
							+ "where ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') "
							+ "and ANY(x IN o.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN l.marked WHERE x = '"+this.alignmentNo+"')"
							+ "and length(FILTER(x in n.annotations WHERE x in m.annotations)) >= "+minCommonAnnotations+" "
							+ "and length(FILTER(x in o.annotations WHERE x in l.annotations)) >= "+minCommonAnnotations+" return n,m,o,l");
					
					while(result.hasNext()){
						Record row = result.next();
						record.clear();
						for ( Entry<String,Object> column : row.asMap().entrySet() ){
							if(column.getValue()!=null)
								switch (column.getKey()) {
								case "n":
									record.add(0,row.get( column.getKey() ).asNode());
									break;
								case "m":
									record.add(1,row.get( column.getKey() ).asNode());
									break;
								case "o":
									record.add(2,row.get( column.getKey() ).asNode());
									break;
								case "l":
									record.add(3,row.get( column.getKey() ).asNode());
									break;
								default:
									System.out.println("Unexpected column"+column.getKey());
									break;
								}
							}
						records.add(new ArrayList<Node>(record));
						}
					
				} else {
					
					result = tx.run("match (n:Organism2 {"+clusterType+": "+clusterIDOfOrganism2+"})<-[s:SIMILARITY]-(m:Organism1 {"+clusterType+": "+clusterIDOfOrganism1+"})"
							+ "-[i1:INTERACTS_1]-(l:Organism1 {"+clusterType+": "+clusterIDOfOrganism1+"})<-[a:ALIGNS]-(o:Organism2 {"+clusterType+": "+clusterIDOfOrganism2+"})-[i2:INTERACTS_2]-(n) "
							+ "where ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') "
							+ "and NOT ANY(x IN o.marked WHERE x = '"+this.alignmentNo+"') and NOT ANY(x IN l.marked WHERE x = '"+this.alignmentNo+"')"
							+ "and length(FILTER(x in n.annotations WHERE x in m.annotations)) >= "+minCommonAnnotations+" "
							+ "return n,m");
					
					while(result.hasNext()){
						Record row = result.next();
						record.clear();
						for ( Entry<String,Object> column : row.asMap().entrySet() ){
							if(column.getValue()!=null)
								switch (column.getKey()) {
								case "n":
									record.add(0,row.get( column.getKey() ).asNode());
									break;
								case "m":
									record.add(1,row.get( column.getKey() ).asNode());
									break;
								default:
									System.out.println("Unexpected column"+column.getKey());
									break;
								}
							}
						records.add(new ArrayList<Node>(record));
						}
					
				}
				
				Set<Node> aligned = new HashSet<Node>();
				System.out.println(addResultsToAlignment(records,aligned,mode)+" mappings were added with alignClusterEdges Method with addPair value = "+addPair);
				unmarkAllNodes();	
			tx.success(); tx.close();
	    } catch (Exception e){
	    	System.out.println("alignClusters: " + e.getMessage());
	    } finally {ieca.close();}
		this.bs = as.calculateGlobalBenchmarks((Aligner)this);	
		return true;
	} );	
	if(!success)
		System.err.println("Method alignClusterEdges was interrupted");
}
// Bu metot işlem sonrasında removeBadMappings ile birlikte kullanılarak rastgele eklenen eşleşmelerin katkısı olanları kullanılır ve katkısı olmayanları bir döngü içinde temizlenir.
public void addMeaninglessMapping(int limit, char mode) {
	System.out.println("addMeaninglessMapping for Aligner "+this.alignmentNo);
	TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
	tm.failure(new Throwable("Herkesin tuttuğu kendine"));
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
	boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
		StatementResult result;
		Session ieca = AkkaSystem.driver.session();
		try ( org.neo4j.driver.v1.Transaction tx = ieca.beginTransaction())
	    {
				markUnalignedNodes();
				ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
				ArrayList<Node> record = new ArrayList<Node>();
				result = tx.run("match (n:Organism2),(m:Organism1) where ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') with n,m,rand() as number return n,m order by number limit "+limit);
				
				while(result.hasNext()){
					Record row = result.next();
					record.clear();
					for ( Entry<String,Object> column : row.asMap().entrySet() ){
						if(column.getValue()!=null)
							switch (column.getKey()) {
							case "n":
								record.add(0,row.get( column.getKey() ).asNode());
								break;
							case "m":
								record.add(1,row.get( column.getKey() ).asNode());
								break;
							default:
								System.out.println("Unexpected column"+column.getKey());
								break;
							}
						}
					records.add(new ArrayList<Node>(record));
					}
				
				Set<Node> aligned = new HashSet<Node>();
				System.out.println(addResultsToAlignment(records,aligned,mode)+" mappings were added with addMeaninglessMapping Method.");
				unmarkAllNodes();	

			tx.success(); tx.close();
	    } catch (Exception e){
	    	System.out.println("addMeaninglessMapping: " + e.getMessage());
	    } finally {ieca.close();}
		this.bs = as.calculateGlobalBenchmarks((Aligner)this);	
		return true;
	} );	
	if(!success)
		System.out.println("Method addMeaninglessMapping was interrupted.");
}

/*
 * k adetten az annotation'u ve sim'in alt�nda benzerli�i olan d���m �iftleri aras�ndaki hizalamay� verilen keepEdges parametresine g�re kenarlar� koruyarak ya da korumadan siler
 * infix kontrol edilecek
 * limit parametresi ile silinen kayıt toplamı 1imit olacak biçimde iki kere silme işlemi yapılabiliyor.
 * */

public void removeBadMappings(int k, double sim, boolean keepEdges, int limit){
	System.out.println("removeBadAlignment for Aligner "+this.alignmentNo);
	TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
	tm.failure(new Throwable("Herkesin tuttuğu kendine"));
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
	int removed = template.with(AkkaSystem.graphDb).execute( transaction -> {
		Session rbm = AkkaSystem.driver.session();
		ResultSummary rs = null; 
		int count = 0;
		String limitInfix ="";
		if(limit>0)
			limitInfix = "with r limit "+limit+" match ()-[r:ALIGNS]-() ";
	try ( org.neo4j.driver.v1.Transaction tx = rbm.beginTransaction())
    {
		if(keepEdges){
			tx.run("match (o:Organism2)-[i2:INTERACTS_2]-(n:Organism2)-[r:ALIGNS {alignmentNumber: '"+alignmentNo+"'}]->(m:Organism1)-[i1:INTERACTS_1]-(l:Organism1)<-[r2:ALIGNS {alignmentNumber: '"+alignmentNo+"'}]-(o) set o.marked = o.marked +'"+this.alignmentNo+"', n.marked = n.marked +'"+this.alignmentNo+"', m.marked = m.marked +'"+this.alignmentNo+"', l.marked = l.marked +'"+this.alignmentNo+"'");			
			rs = tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where length(FILTER(x in n.annotations WHERE x in m.annotations)) < "+k+" and not (m:Organism1)-[:SIMILARITY]->(n) and NOT ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and NOT ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') and r.alignmentNumber = '"+alignmentNo+"' "+limitInfix+"delete r").consume();
			count+=rs.counters().relationshipsDeleted();	
			if(limit-count>0 || limit<1) {
				if(limit-count>0)
					limitInfix = "with r limit "+(limit-count)+" match ()-[r:ALIGNS]-() ";
				if(limit<1)
					limitInfix = "";
				rs = tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1)-[s:SIMILARITY]->(n) where length(FILTER(x in n.annotations WHERE x in m.annotations)) < "+k+" and s.similarity < "+sim+" and NOT ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and NOT ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') and r.alignmentNumber = '"+alignmentNo+"' "+limitInfix+"delete r").consume();
				count+=rs.counters().relationshipsDeleted();
			}
			tx.run("MATCH (n) SET n.marked = FILTER(x IN n.marked WHERE x <> '"+this.alignmentNo+"')");
		} else {
			rs = tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where length(FILTER(x in n.annotations WHERE x in m.annotations)) < "+k+" and not (m:Organism1)-[:SIMILARITY]->(n) and r.alignmentNumber = '"+alignmentNo+"' "+limitInfix+"delete r").consume();
			count+=rs.counters().relationshipsDeleted();
			if(limit-count>0 || limit<1) {
				if(limit-count>0)
					limitInfix = "with r limit "+(limit-count)+" match ()-[r:ALIGNS]-() ";
				if(limit<1)
					limitInfix = "";
				rs = tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1)-[s:SIMILARITY]->(n) where length(FILTER(x in n.annotations WHERE x in m.annotations)) < "+k+" and s.similarity < "+sim+" and r.alignmentNumber = '"+alignmentNo+"' "+limitInfix+"delete r").consume();
				count+=rs.counters().relationshipsDeleted();
			}	
		}
		tx.success(); tx.close();
    } catch (Exception e){
    	System.out.println("removeBadMappings: " + e.getMessage());
    	if(Math.random() < 0.5)
    	removeBadMappings(k, sim, keepEdges,limit);
      }finally {rbm.close();}
	this.bs = as.calculateGlobalBenchmarks((Aligner)this);	
	return count;
} );
	
	if(removed>0&&(k>1||sim>1))
		noofCyclesAlignmentUnchanged = 0;	
	
	if(keepEdges)
	System.err.println(removed+" mappings without edges having less than "+k+" annotations and "+sim+" similarity were removed from aligner "+this.alignmentNo+" with removeBadMapping Method.");
	else
		System.err.println(removed+" mappings having less than "+k+" annotations and "+sim+" similarity were removed from aligner "+this.alignmentNo+" with removeBadMapping Method.");
}
// daha son haliyle denenmedi. rand() eklenerek rastgeleleştirilebilir.
public void removeMappingsWithoutEdges(int limit){
	System.out.println("removeAlignmentsWithoutEdges for Aligner "+this.alignmentNo);
	TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
	tm.failure(new Throwable("Herkesin tuttuğu kendine"));
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
	int removed = template.with(AkkaSystem.graphDb).execute( transaction -> {
		Session rmwe = AkkaSystem.driver.session();
		ResultSummary rs = null;
		String limitInfix = "with r limit "+limit+" match ()-[r:ALIGNS]-() ";
	try ( org.neo4j.driver.v1.Transaction tx = rmwe.beginTransaction())
    {	
		tx.run("match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[t:ALIGNS]->(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)<-[s:ALIGNS]-(o) where s.alignmentNumber = '"+alignmentNo+"' and t.alignmentNumber = '"+alignmentNo+"' set o.marked = o.marked +'"+this.alignmentNo+"', p.marked = p.marked +'"+this.alignmentNo+"', n.marked = n.marked +'"+this.alignmentNo+"', m.marked = m.marked +'"+this.alignmentNo+"'");	
		rs = tx.run("match (n:Organism2)-[r:ALIGNS {alignmentNumber: '"+alignmentNo+"'}]->(m:Organism1) where NOT ANY(x IN n.marked WHERE x = '"+this.alignmentNo+"') and NOT ANY(x IN m.marked WHERE x = '"+this.alignmentNo+"') "+limitInfix+"delete r").consume();
		tx.run("MATCH (n) SET n.marked = FILTER(x IN n.marked WHERE x <> '"+this.alignmentNo+"')");
		tx.success(); tx.close();
    } catch (Exception e){
    	  System.out.println("Remove Alignments Without Edges::: "+e.getMessage());
    	  removeMappingsWithoutEdges(limit);
      }finally {rmwe.close();}
	this.bs = as.calculateGlobalBenchmarks((Aligner)this);	
	return rs.counters().relationshipsDeleted();
} );	
	System.out.println(removed+" mappings without edges were removed from aligner "+this.alignmentNo+" with removeMappingWithoutEdges Method.");
}

/*
 * ICS ve S3 olcutlerini iyilestirmek icin b�l�nen k�s�mlar�nda yer alan countInducedSubGraphEdgesOfANetwork metodundan d�n�len sonucu k���lt�r. 
 * ikinci aşamada addMeaninglessMappings ile birlikte çalıştırılır.
 * */

public void removeBadMappingsToReduceInduction1(int k, double sim, boolean keepEdges,int simTreshold, int annotationTreshold,int powerTreshold){
	System.out.println("removeBadAlignmentToReduceInduction1 for Aligner "+this.alignmentNo);
	
	TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
	tm.failure(new Throwable("Herkesin tuttuğu kendine"));
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
	int removed = template.with(AkkaSystem.graphDb).execute( transaction -> {
		Session rmwe = AkkaSystem.driver.session();
		StatementResult result;
		int count = 0;
		try ( org.neo4j.driver.v1.Transaction tx = rmwe.beginTransaction())
	    {
			if(keepEdges){
				tx.run("match (o:Organism2)-[i2:INTERACTS_2]-(n:Organism2)-[r:ALIGNS]->(m:Organism1)-[i1:INTERACTS_1]-(l:Organism1)<-[r2:ALIGNS]-(o) "
						+ "where r.alignmentNumber = '"+alignmentNo+"' and r2.alignmentNumber = '"+alignmentNo+"' set o.marked = o.marked + '"+this.alignmentNo+"', n.marked = n.marked + '"+this.alignmentNo+"', m.marked = m.marked + '"+this.alignmentNo+"', l.marked = l.marked + '"+this.alignmentNo+"'");
//				result = tx.run( "optional match (n)-[ss:SIMILARITY]->(p:Organism2)-[t:ALIGNS]->(n:Organism1)-[r:INTERACTS_1]->(m:Organism1)<-[a:ALIGNS]-(o:Organism2)<-[s:SIMILARITY]-(m) "
//				+ "where (s.similarity < "+sim+" or s.similarity = null) and (ss.similarity < "+sim+" or ss.similarity = null) "
//				+ "and length(FILTER(x in p.annotations WHERE x in n.annotations)) < "+k+" and length(FILTER(x in o.annotations WHERE x in m.annotations)) < "+k+" "
//				//+"and n.marked = false and p.marked = false and m.marked = false and o.marked = false "
//				+"and p.marked = false and o.marked = false "
//				+ "and a.alignmentNumber = '"+alignmentNo+"' and t.alignmentNumber = '"+alignmentNo+"' return t,a");
			}
				result = tx.run( "match (p:Organism2)-[t:ALIGNS]->(n:Organism1)-[r:INTERACTS_1]->(m:Organism1)<-[a:ALIGNS]-(o:Organism2) "
				+ "where NOT ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and NOT ANY(x IN o.marked WHERE x = '"+this.alignmentNo+"') " //(p.marked = false or n = false ) and (o.marked = false or m = false)
				+ "and a.alignmentNumber = '"+alignmentNo+"' and t.alignmentNumber = '"+alignmentNo+"' return id(t),id(a)");
				
				long t = 0;
				long a = 0;
				long temp = 0;
				
				
				while(result.hasNext()){
					Record row = result.next();
					
					for ( Entry<String,Object> column : row.asMap().entrySet() ){
						if(column.getValue()!=null)
							switch (column.getKey()) {
							case "id(t)":
								t = row.get( column.getKey() ).asLong();
								break;
							case "id(a)":
								a = row.get( column.getKey() ).asLong();
								break;
							default:
								System.out.println("Unexpected column"+column.getKey());
								break;
							}
						}
					temp = compareSimilarityContribution(t, a,simTreshold, annotationTreshold,powerTreshold);
					if (temp==t)
						tx.run("match ()-[a:ALIGNS]-() where id(a) ="+a+" delete a");
					else /*if (temp==a)*/
						tx.run("match ()-[t:ALIGNS]-() where id(t) ="+t+" delete t");
					t = 0;
					a = 0;
					if(temp!=0)
						count++;
					temp = 0;
					}
				
				if(keepEdges)
				tx.run("MATCH (n) SET n.marked = FILTER(x IN n.marked WHERE x <> '"+this.alignmentNo+"')");
			tx.success(); tx.close();
	    } catch (Exception e){
	    	  e.printStackTrace();
	      }
	this.bs = as.calculateGlobalBenchmarks((Aligner)this);	
	return count;
} );	
	System.err.println(removed+" induced edges were removed in Aligner "+this.alignmentNo);
	this.bs = as.calculateGlobalBenchmarks(this);
}
// Diğer ağ için
public void removeBadMappingsToReduceInduction2(int k, double sim, boolean keepEdges,int simTreshold, int annotationTreshold,int powerTreshold){
	System.out.println("removeBadAlignmentToReduceInduction2 for Aligner "+this.alignmentNo);
	StatementResult result;
	
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
    {
		if(keepEdges){
			tx.run("match (o:Organism2)-[i2:INTERACTS_2]-(n:Organism2)-[r:ALIGNS]->(m:Organism1)-[i1:INTERACTS_1]-(l:Organism1)<-[r2:ALIGNS]-(o) "
					+ "where r.alignmentNumber = '"+alignmentNo+"' and r2.alignmentNumber = '"+alignmentNo+"' set o.marked = o.marked + '"+this.alignmentNo+"', n.marked = n.marked + '"+this.alignmentNo+"', m.marked = m.marked + '"+this.alignmentNo+"', l.marked = l.marked + '"+this.alignmentNo+"'");
		}
			result = tx.run( "match (p:Organism1)<-[t:ALIGNS]-(n:Organism2)-[r:INTERACTS_2]->(m:Organism2)-[a:ALIGNS]->(o:Organism1) "
			+ "where NOT ANY(x IN p.marked WHERE x = '"+this.alignmentNo+"') and NOT ANY(x IN o.marked WHERE x = '"+this.alignmentNo+"') " //(p.marked = false or n = false ) and (o.marked = false or m = false)
			+ "and a.alignmentNumber = '"+alignmentNo+"' and t.alignmentNumber = '"+alignmentNo+"' return id(t),id(a)");
			
			long t = 0;
			long a = 0;
			long temp = 0;
			
			while(result.hasNext()){
				Record row = result.next();
				
				for ( Entry<String,Object> column : row.asMap().entrySet() ){
					if(column.getValue()!=null)
						switch (column.getKey()) {
						case "id(t)":
							t = row.get( column.getKey() ).asLong();
							break;
						case "id(a)":
							a = row.get( column.getKey() ).asLong();
							break;
						default:
							System.out.println("Unexpected column"+column.getKey());
							break;
						}
					}
				temp = compareSimilarityContribution(t, a, simTreshold,  annotationTreshold, powerTreshold);
				if (temp==t)
					tx.run("match ()-[a:ALIGNS]-() where id(a) ="+a+" delete a");
				else /*if (temp==a)*/
					tx.run("match ()-[t:ALIGNS]-() where id(t) ="+t+" delete t");
				t = 0;
				a = 0;
				temp = 0;
				}
			
			if(keepEdges)
			tx.run("MATCH (n) SET n.marked = FILTER(x IN n.marked WHERE x <> '"+this.alignmentNo+"')");
		tx.success(); tx.close();
    } catch (Exception e){
    	  e.printStackTrace();
      }
	this.bs = as.calculateGlobalBenchmarks(this);
}

/*
 * Inductioni azaltma isini hizalanan dugumler arasindaki kenarlari isaretleyerek yapmaya calisan metot
 * Veritabani loguna hata gonderiyordu. Su anda gondermiyor.
 * Cok fazla sayida karsilikli korunan kenari siliyor. removeAlignmentsWithoutEdges ile birlikte calistirildiginda anlamli bir kume kaliyor. 
 * */
public void reduceInduction1(int simTreshold, int annotationTreshold,int powerTreshold){
	System.out.println("reduceInduction1 for Aligner "+this.alignmentNo);
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
    {
	tx.run("match (o:Organism2)-[i2:INTERACTS_2]-(n:Organism2)-[r:ALIGNS]->(m:Organism1)-[i1:INTERACTS_1]-(l:Organism1)<-[r2:ALIGNS]-(o) "
			+ "where r.alignmentNumber = '"+alignmentNo+"' and r2.alignmentNumber = '"+alignmentNo+"'"		
			+ "set i2.marked = i2.marked +'"+this.alignmentNo+"', i1.marked = i1.marked +'"+this.alignmentNo+"'");																			//false idi true yaptık
	StatementResult result = tx.run("match (p:Organism2)-[t:ALIGNS]->(n:Organism1)-[r:INTERACTS_1]->(m:Organism1)<-[a:ALIGNS]-(o:Organism2) where NOT ANY(x IN r.marked WHERE x = '"+this.alignmentNo+"') return id(t),id(a)");
	
	long t = 0;
	long a = 0;
	long temp = 0;
	
	while(result.hasNext()){
		Record row = result.next();
		
		for ( Entry<String,Object> column : row.asMap().entrySet() ){
			if(column.getValue()!=null)
				switch (column.getKey()) {
				case "id(t)":
					t = row.get( column.getKey() ).asLong();
					break;
				case "id(a)":
					a = row.get( column.getKey() ).asLong();
					break;
				default:
					System.out.println("Unexpected column"+column.getKey());
					break;
				}
			}
		temp = compareSimilarityContribution(t, a,simTreshold, annotationTreshold,powerTreshold);
		if (temp==t)
			tx.run("match ()-[a:ALIGNS]-() where id(a) ="+a+" delete a");
		else /*if (temp==a)*/
			tx.run("match ()-[t:ALIGNS]-() where id(t) ="+t+" delete t");
		t = 0;
		a = 0;
		temp = 0;
		}

	//tx.run("match p=()-[r]-() FOREACH (x IN relationships(p) | SET x.marked["+this.alignmentNo+"] = false )");
	tx.run("match p=()-[r]-() SET r.marked = FILTER(x IN n.marked WHERE x <> '"+this.alignmentNo+"')");
	tx.success(); tx.close();
    } catch (Exception e){
    	  e.printStackTrace();
      }
	this.bs = as.calculateGlobalBenchmarks(this);
    }

//Diğer ağın büyük olma durumuna göre yukaridakinin tersi hale getirilecek. Yukaridaki calismadigina gore bu da calismaz.

public void reduceInduction2(){
	System.out.println("reduceInduction2 for Aligner "+this.alignmentNo);
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
    {
	tx.run("match (o:Organism2)-[i2:INTERACTS_2]-(n:Organism2)-[r:ALIGNS]->(m:Organism1)-[i1:INTERACTS_1]-(l:Organism1)<-[r2:ALIGNS]-(o) "
			+ "where r.alignmentNumber = '"+alignmentNo+"' and r2.alignmentNumber = '"+alignmentNo+"'"		
			+ "set i2.marked = i2.marked +'"+this.alignmentNo+"', i1.marked = i1.marked +'"+this.alignmentNo+"'");
	tx.run("match (p:Organism1)<-[t:ALIGNS]-(n:Organism2)-[r:INTERACTS_2]->(m:Organism2)-[a:ALIGNS]->(o:Organism1) where NOT ANY(x IN r.marked WHERE x = '"+this.alignmentNo+"') "
			+ "and length(FILTER(x in p.annotations WHERE x in n.annotations)) > length(FILTER(x in o.annotations WHERE x in m.annotations)) delete a");
	//tx.run("match p=()-[r]-() FOREACH (x IN relationships(p) | SET x.marked["+this.alignmentNo+"] = false )");
	tx.run("match p=()-[r]-() SET r.marked = FILTER(x IN n.marked WHERE x <> '"+this.alignmentNo+"')");
	tx.success(); tx.close();
    } catch (Exception e){
    	  e.printStackTrace();
      }
	this.bs = as.calculateGlobalBenchmarks(this);
}
// removeAlignmentsWithoutEdges ile birlikte calistirildiginda reduceInductiondakinden de kucuk ve belirgin bicimde dogru bir alt cizge uretiyor.
public void removeInductiveMappings1(int simTreshold, int annotationTreshold,int powerTreshold){
	System.out.println("removeInductiveAlignments1 for Aligner "+this.alignmentNo);
	StatementResult result;
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
    {
	result = tx.run("match (q:Organism2)-[a2:ALIGNS {alignmentNumber: '"+alignmentNo+"'}]->(n:Organism1)-[INTERACTS_1]-(m:Organism1)<-[a1:ALIGNS {alignmentNumber: '"+alignmentNo+"'}]-(p:Organism2) where not (p)-[:INTERACTS_2]-(q) "
			+ "return id(a1),id(a2)");
	
	long a1 = 0;
	long a2 = 0;
	long temp = 0;
	
	while(result.hasNext()){
		Record row = result.next();
		
		for ( Entry<String,Object> column : row.asMap().entrySet() ){
			if(column.getValue()!=null)
				switch (column.getKey()) {
				case "id(a1)":
					a1 = row.get( column.getKey() ).asLong();
					break;
				case "id(a2)":
					a2 = row.get( column.getKey() ).asLong();
					break;
				default:
					System.out.println("Unexpected column"+column.getKey());
					break;
				}
			}
		temp = compareSimilarityContribution(a1, a2,simTreshold,  annotationTreshold, powerTreshold);
		if (temp==a1)
			tx.run("match ()-[a2:ALIGNS]-() where id(a2) ="+a2+" delete a2");
		else /*if (temp==a)*/
			tx.run("match ()-[a1:ALIGNS]-() where id(a1) ="+a1+" delete a1");
		a1 = 0;
		a2 = 0;
		temp = 0;
		}
	
	tx.success(); tx.close();
    } catch (Exception e){
    	  e.printStackTrace();
      }
	this.bs = as.calculateGlobalBenchmarks(this);
}

public void removeInductiveMappings2(){
	System.out.println("removeInductiveAlignments2 for Aligner "+this.alignmentNo);
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
    {
	tx.run("match (q:Organism1)<-[a2:ALIGNS {alignmentNumber: '"+alignmentNo+"'}]-(n:Organism2)-[INTERACTS_2]-(m:Organism2)-[a1:ALIGNS {alignmentNumber: '"+alignmentNo+"'}]->(p:Organism1) where not (p)-[:INTERACTS_1]-(q) "
			+ "and length(FILTER(x in p.annotations WHERE x in m.annotations)) > length(FILTER(x in q.annotations WHERE x in n.annotations)) delete a2");
	
	
	
	tx.success(); tx.close();
    } catch (Exception e){
    	  e.printStackTrace();
      }
	this.bs = as.calculateGlobalBenchmarks(this);
}

/*
 * returns the id of the alignment relationship that has bigger/smaller similarity and annotation contribution
 * olmuyordu! return case when id(r2) > id(r1) then id(r2) when id(r1) >= id(r2) then id(r1) else -1 end as greater
 * �al���yor ama alttaki metotla birebir ayn� sonu�lar� �retmiyor.
 * */

@SuppressWarnings("unused")
private long compareContribution(long relationshipID1, long relationshipID2){
	StatementResult result;
	StatementResult result1;
	StatementResult result2 = null;
	int commonAnnotations1 = 0;
	int commonAnnotations2 = 0;
	double similarity1 = 0.0;
	double similarity2 = 0.0;
	Record r = null;
	try ( org.neo4j.driver.v1.Transaction tx = innerSession.beginTransaction())
    {
		System.out.println(relationshipID1+" - "+relationshipID2);
		//result = tx.run("match (n1:Organism2)-[r1:ALIGNS]->(p1:Organism1)-[t1:SIMILARITY]->(n1), (n2:Organism2)-[r2:ALIGNS]->(p2:Organism1)-[t2:SIMILARITY]->(n2) where length(FILTER(x in p2.annotations WHERE x in n2.annotations)) >= length(FILTER(x in p1.annotations WHERE x in n1.annotations)) and t2.similarity >= t1.similarity"
		result = tx.run("match (n1:Organism2)-[r1:ALIGNS]->(p1:Organism1), (n2:Organism2)-[r2:ALIGNS]->(p2:Organism1) "
				+ "where r2.alignmentNumber = '"+alignmentNo+"' and r1.alignmentNumber = '"+alignmentNo+"' and id(r2) = "+relationshipID2+" and id(r1) = "+relationshipID1+" "
				+ "return length(FILTER(x in p2.annotations WHERE x in n2.annotations)) as commonAnnotations2, length(FILTER(x in p1.annotations WHERE x in n1.annotations)) as commonAnnotations1,id(p1),id(n1),id(p2),id(n2) ");
		
		r = result.single();
		commonAnnotations1 = Integer.parseInt(r.get("commonAnnotations1").toString());
		commonAnnotations2 = Integer.parseInt(r.get("commonAnnotations2").toString());

		result1 = tx.run("match (p1:Organism1)-[t1:SIMILARITY]->(n1:Organism2) where id(p1) = '"+Integer.parseInt(r.get("id(p1)").toString())+"' and id(n1) = "+Integer.parseInt(r.get("id(n1)").toString())+" return t1.similarity");
		result2 = tx.run("match (p2:Organism1)-[t2:SIMILARITY]->(n2:Organism2) where id(p2) = '"+Integer.parseInt(r.get("id(p2)").toString())+"' and id(n2) = "+Integer.parseInt(r.get("id(n2)").toString())+" return t2.similarity");
		
		similarity1 = Double.parseDouble(result1.single().get("t1.similarity").toString());
		similarity2 = Double.parseDouble(result2.single().get("t2.similarity").toString());
		
		tx.success(); tx.close();
    } 
		catch(NoSuchRecordException nsre){
			try {
				if (similarity1 == 0)
					System.out.println("No Such Record");
			similarity2 = Double.parseDouble(r.get("t2.similarity").toString());
			}
			catch(NumberFormatException nfe){
				System.out.println("Number Format Exception");
			} catch (NoSuchRecordException nsre2){
				System.out.println("No Such Record");
			}
		}
	
    catch (Exception e){
    	  e.printStackTrace();
      } finally {
	System.out.println("-------------");
	System.out.println("Common Annotations1: "+commonAnnotations1);
	System.out.println("Common Annotations2: "+commonAnnotations2);
	System.out.println("similarity1: "+similarity1);
	System.out.println("similarity2: "+similarity2);
	System.out.println("-------------");
      }
	if (commonAnnotations1*similarity1 > commonAnnotations2*similarity2)
		return relationshipID1;
	else if (commonAnnotations1*similarity1 < commonAnnotations2*similarity2)
		return relationshipID2;
	else if (similarity1 > similarity2)
		return relationshipID1;
	else if (similarity1 < similarity2)
		return relationshipID2;
	else if (commonAnnotations1 > commonAnnotations2)
		return relationshipID1;
	else if (commonAnnotations1 < commonAnnotations2)
		return relationshipID2;
		return 0;
}

/*
 * compareContributiondan daha duzgun yazilmis bir karsilastirma metodu.
 * Bu karşılaştırmada power kullanılıyor.
 * */

private long compareSimilarityContribution(long relationshipID1, long relationshipID2,int simTreshold, int annotationTreshold,int powerTreshold){
	
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
//			System.out.println(relationshipID1+" - "+relationshipID2);
//			result = tx.run("match (n1:Organism2)-[r1:ALIGNS]->(p1:Organism1)-[t1:SIMILARITY]->(n1), (n2:Organism2)-[r2:ALIGNS]->(p2:Organism1)-[t2:SIMILARITY]->(n2) where length(FILTER(x in p2.annotations WHERE x in n2.annotations)) >= length(FILTER(x in p1.annotations WHERE x in n1.annotations)) and t2.similarity >= t1.similarity"
//			result = tx.run("optional match (n1:Organism2)-[r1:ALIGNS]->(p1:Organism1)-[t1:SIMILARITY]->(n1), (n2:Organism2)-[r2:ALIGNS]->(p2:Organism1)-[t2:SIMILARITY]->(n2)"
//					+ "where r2.alignmentNumber = '"+alignmentNo+"' and r1.alignmentNumber = '"+alignmentNo+"' and id(r2) = "+relationshipID2+" and id(r1) = "+relationshipID1+" "
//					+ "return length(FILTER(x in p2.annotations WHERE x in n2.annotations)) as commonAnnotations2, length(FILTER(x in p1.annotations WHERE x in n1.annotations)) as commonAnnotations1,t2.similarity,t1.similarity,id(p1),id(n1),id(p2),id(n2) ");
			
			result = tx.run("match (n1:Organism2)-[r1:ALIGNS]->(p1:Organism1), (n2:Organism2)-[r2:ALIGNS]->(p2:Organism1) "
					+ "where r2.alignmentNumber = '"+alignmentNo+"' and r1.alignmentNumber = '"+alignmentNo+"' and id(r2) = "+relationshipID2+" and id(r1) = "+relationshipID1+" "
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
//				System.out.println("1st Similarity Number Format Exception: "+nfe.getMessage());
			}
			try {
				similarity2 = Double.parseDouble(r.get("t2.similarity").toString());
			} catch (Exception e) {
//				System.out.println("2nd Similarity Number Format Exception: "+e.getMessage());
			}		
			
			tx.success(); tx.close();
	    } 
			catch(NoSuchRecordException nsre){
//				System.out.println("No Such Record");
			}
		
	    catch (Exception e){
	    	  System.err.println("compareSimilarityContribution::: "+e.getMessage());
	    	  compareSimilarityContribution(relationshipID1, relationshipID2,simTreshold,annotationTreshold,powerTreshold);
	      } finally {
//		System.out.println("-------------");
//		System.out.println("Common Annotations1: "+commonAnnotations1);
//		System.out.println("Common Annotations2: "+commonAnnotations2);
//		System.out.println("similarity1: "+similarity1);
//		System.out.println("similarity2: "+similarity2);
//		System.out.println("-------------");
	    	  csc.close();
	      }
		
		if(powersum1<powerTreshold&&powersum2<powerTreshold)
			return 0L;
		if(similarity1<simTreshold&&similarity2<simTreshold)
			return 0L;
		if(commonAnnotations1<annotationTreshold&&commonAnnotations2<annotationTreshold)
			return 0L;
		
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

public void removeAlignment(){
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction()  )
	{
		tx.run( "match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where r.alignmentNumber = '"+ alignmentNo +"' delete r");
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}	
	this.bs = as.calculateGlobalBenchmarks(this);
}

/**
 * �lgili hizalaman�n sadece i�aretli k�sm� silinir.
 * 
 * */

public void removeAlignment(String markedQuery){
	try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction()  )
	{
		tx.run( "match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where r.alignmentNumber = '"+ alignmentNo +"' and ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') and ANY(x IN m.markedQuery WHERE x = '"+markedQuery+"') delete r");
		tx.success(); tx.close();
	} catch (Exception e){
		e.printStackTrace();
	}	
	this.bs = as.calculateGlobalBenchmarks(this);
}

/*
 * Daha eski olan hizalamalar korunur. 
 * startNode(r) endNode(r)
 * Hizalamalar b�yle bulunuyor:
 * match (n:Organism2)-[r:ALIGNS]->(m:Organism1), (o:Organism2)-[q:ALIGNS]->(Organism1) where startNode(r) = startNode(q) and r.alignmentNumber = q.alignmentNumber return r union match (n:Organism2)-[r:ALIGNS]->(m:Organism1), (o:Organism2)-[q:ALIGNS]->(Organism1) where endNode(r) = endNode(q) and r.alignmentNumber = q.alignmentNumber return r
 * 
 * */ 

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
		rs = tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1), (o:Organism2)-[q:ALIGNS]->(p:Organism1) where startNode(r) = startNode(q) and r.alignmentNumber = '"+alignmentNo+"' and q.alignmentNumber = '"+alignmentNo+"' and id(r) >= id(q) delete r").consume();
		count+=rs.counters().relationshipsDeleted();
		rs = tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1), (o:Organism2)-[q:ALIGNS]->(p:Organism1) where endNode(r) = endNode(q) and r.alignmentNumber = '"+alignmentNo+"' and q.alignmentNumber = '"+alignmentNo+"' and id(r) >= id(q) delete r").consume();
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
		System.err.println(removed+" Subsequent Mappings are Removed from "+this.alignmentNo+" !!!");
	this.bs = as.calculateGlobalBenchmarks(this);
}

/*
 * K�yaslama puanlar�na en fazla katk�s� olan hizalamalar korunur.
 * */

public void removeWeakerOfManyToManyAlignments(int simTreshold, int annotationTreshold,int powerTreshold){
	
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
			+ "where r.alignmentNumber = '"+alignmentNo+"' and q.alignmentNumber = '"+alignmentNo+"' and (startNode(r) = startNode(q) or endNode(r) = endNode(q)) return id(r),id(q)");
			
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
				
				temp = compareSimilarityContribution(r, q,simTreshold, annotationTreshold,powerTreshold);
				if (temp==r)
				{
					rs = tx.run("match ()-[q:ALIGNS]-() where id(q) ="+q+" delete q").consume();
					count+=rs.counters().relationshipsDeleted();
					rs = null;
				}
				else
				{
					rs = tx.run("match ()-[r:ALIGNS]-() where id(r) ="+r+" delete r").consume();
					count+=rs.counters().relationshipsDeleted();
					rs = null;
				}
				r = 0;
				q = 0;
				temp = 0;
				
				}
			
			result = tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1), (o:Organism2)-[q:ALIGNS]->(p:Organism1) where endNode(r) = endNode(q) and r.alignmentNumber = q.alignmentNumber return id(r),id(q)");
			
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
				
				temp = compareSimilarityContribution(r, q, simTreshold, annotationTreshold, powerTreshold);
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
	    		removeWeakerOfManyToManyAlignments( simTreshold, annotationTreshold, powerTreshold);
	      }
		
		finally {rwmma.close();}
	    return count;
	} );
		System.err.println(removed+" Weaker Mappings are removed!!!");
	this.bs = as.calculateGlobalBenchmarks(this);
}
/*
 * Buradaki yanl��
 * Herhangi bir hizalaman�n startNode u ya da endNode u olmayan d���mler bulunacak.
 * 
 * match ()-[rr:ALIGNS]->(), (p:Organism2), (n:Organism1) where rr.alignmentIndex starts with (p.proteinName+'*') or rr.alignmentIndex ends with ('*'+n.proteinName) return p,n gereksiz
 * 
 * Organism 1 deki t�m d���mlerden match ()-[rr:ALIGNS]->(n:Organism1) return n daki d���mleri ��kar.
 * Organism 2 deki t�m d���mlerden match (p:Organism2)-[rr:ALIGNS]->() return p daki d���mleri ��kar.
 * Bu ikisini topla
 * 
 * 
 * Alternatif: Bunun tersi $ match ()-[rr:ALIGNS]->() return startNode(rr),endNode(rr)
 * 
 * Son: 
 * match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) return x
 * match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) return x
 * 
 * match (nn:Organism2)-[r:ALIGNS]->(mm:Organism1) with collect(nn) as deneme2, collect(mm) as dene2 match (xx:Organism2) where not (xx in deneme2) return xx as giray union match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) return x as giray
 * 
 * marking organism2
 * match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match p=(x:Organism2) where not (x in deneme) FOREACH (nn IN nodes(p)| SET nn.marked = true )
 * marking organism1
 * match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match p=(x:Organism1) where not (x in dene) FOREACH (nn IN nodes(p)| SET nn.marked = true )
 * 
 * */

public SubGraph findUnalignedNodes(){
	
	TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
	tm.failure(new Throwable("Herkesin tuttuğu kendine"));
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
	SubGraph sg = template.with(AkkaSystem.graphDb).execute( transaction -> {
		Session unAlignedSession = AkkaSystem.driver.session();
	SubGraph s = new SubGraph(this);
	StatementResult result;
	
	try ( org.neo4j.driver.v1.Transaction tx = unAlignedSession.beginTransaction())
    {
		//result = tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where not exists(r.id) or r.alignmentNumber <> "+alignmentNo+" return (n),(m)");
		result = tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) where rr.alignmentNumber = '"+ alignmentNo +"' with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) return x");	
		
		while(result.hasNext()){
			Record row = result.next();
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null && column.getKey()=="x")
						s.nodes2.add(row.get( column.getKey() ).asNode());
			}
		}
		
		result = tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) where rr.alignmentNumber = '"+ alignmentNo +"' with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) return x");
		
		while(result.hasNext()){
			Record row = result.next();
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null && column.getKey()=="x")
						s.nodes1.add(row.get( column.getKey() ).asNode());
			}
		}
		
		tx.success(); tx.close();
    } catch (Exception e){
    	System.err.println("Find Unaligned nodes::: "+e.getMessage());
    	  findUnalignedNodes();
      } finally {unAlignedSession.close();}
    return s;
} );
if(sg == null)
	System.err.println("HERKESİN TUTTUĞU KENDİNE");
	this.unalignedNodes = sg;
	return sg;
}
// Future<SubGraph> currentAlignment(ExecutionContextExecutor dispatcher) metodunun Futuresızı
public SubGraph findAlignedNodesRelationships() {
	TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
	tm.failure(new Throwable("Herkesin tuttuğu kendine"));
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
	SubGraph sg = template.with(AkkaSystem.graphDb).execute( transaction -> {
		Session unAlignedSession = AkkaSystem.driver.session();
	SubGraph s = new SubGraph(this);
	StatementResult result = null;
	try ( org.neo4j.driver.v1.Transaction tx = unAlignedSession.beginTransaction())
    {
		result = tx.run( "match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where r.alignmentNumber = '"+ alignmentNo +"' return (m),(n),(r)");
		
		while(result.hasNext()){
			Record row = result.next();
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null)
					switch (column.getKey()){
				case "m":
					s.nodes1.add((Node) row.get( column.getKey() ).asNode());
					break;
				case "n":
					s.nodes2.add((Node) row.get( column.getKey() ).asNode());
					break;
				case "r":
					s.aligns.add((Relationship) row.get( column.getKey() ).asRelationship());
					break;
				default:
					System.out.println("Unexpected column"+column.getKey());
					break;
				}		
//				System.out.printf( "%s = %s%n", column.getKey() , row.get(column.getKey() ) );
			}
		}
		tx.success(); tx.close();
    } catch (Exception e){
      System.err.println("Find Aligned Nodes and Relationships::: "+e.getMessage());
  	  findAlignedNodesRelationships();
      } finally {unAlignedSession.close();}
    return s;
} );
if(sg == null)
	System.err.println("HERKESİN TUTTUĞU KENDİNE");
	return sg;
}

private void markUnalignedNodes(){
	TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
	tm.failure(new Throwable("Herkesin tuttuğu kendine"));
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
	boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
		Session unAlignedSession = AkkaSystem.driver.session();
		try(org.neo4j.driver.v1.Transaction t = unAlignedSession.beginTransaction()){
			//t.wait();
			t.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1)  where rr.alignmentNumber = '"+ alignmentNo +"' with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = x.marked + '"+this.alignmentNo+"'");
			t.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1)  where rr.alignmentNumber = '"+ alignmentNo +"' with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = x.marked + '"+this.alignmentNo+"'");
			//t.notifyAll();
			t.success(); t.close();
		} catch(Exception e){
			System.err.println("Mark Unaligned nodes::: "+e.getMessage());
			markUnalignedNodes();
		} finally {unAlignedSession.close();}
	    return true;
	} );
	if(!success)
		System.err.println("HERKESİN TUTTUĞU KENDİNE");
//	Session unAlignedSession = AkkaSystem.driver.session();
//	try(org.neo4j.driver.v1.Transaction t = unAlignedSession.beginTransaction()){
//
//		//t.wait();
//		t.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1)  where rr.alignmentNumber = '"+ alignmentNo +"' with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = x.marked + '"+this.alignmentNo+"'");
//		t.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1)  where rr.alignmentNumber = '"+ alignmentNo +"' with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = x.marked + '"+this.alignmentNo+"'");
//		//t.notifyAll();
//		t.success(); t.close();
//	} catch(Exception e){
//		e.printStackTrace();
//	} finally {unAlignedSession.close();}
}

private void unmarkAllNodes(){
	TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
	tm.failure(new Throwable("Herkesin tuttuğu kendine"));
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
	boolean success = template.with(AkkaSystem.graphDb).execute( transaction -> {
		Session unMarkSession = AkkaSystem.driver.session();
		try(org.neo4j.driver.v1.Transaction t = unMarkSession.beginTransaction()){
			//unMarkSession.wait();
			//t.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked["+this.alignmentNo+"] = false )");
			t.run("MATCH (n) SET n.marked = FILTER(x IN n.marked WHERE x <> '"+this.alignmentNo+"')");
			t.success(); t.close();
			//unMarkSession.notifyAll();
		} catch(Exception e){
			System.err.println("Unmark all nodes::: "+e.getMessage());
			unmarkAllNodes();
		} finally {unMarkSession.close();}
		return true;
	} );
if(!success)
	System.err.println("HERKESİN TUTTUĞU KENDİNE");
//	Session unMarkSession = AkkaSystem.driver.session();
//	try{
//		//unMarkSession.wait();
//		//t.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked["+this.alignmentNo+"] = false )");
//		unMarkSession.run("MATCH (n) SET n.marked = FILTER(x IN n.marked WHERE x <> '"+this.alignmentNo+"')");
//		//t.success(); t.close();
//		//unMarkSession.notifyAll();
//	} catch(Exception e){
//		e.printStackTrace();
//	} finally {unMarkSession.close();}
}

@SuppressWarnings("unused")
private void markUnalignedNodes1()
{

    // START SNIPPET: retry
    Throwable txEx = null;
    int RETRIES = 5;
    int BACKOFF = 3000;
    for ( int i = 0; i < RETRIES; i++ )
    {
        try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
        {
        	tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1)  where rr.alignmentNumber = '"+ alignmentNo +"' with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = x.marked + '"+this.alignmentNo+"'");
			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1)  where rr.alignmentNumber = '"+ alignmentNo +"' with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = x.marked + '"+this.alignmentNo+"'");
			tx.success(); tx.close();
        }
        catch ( Throwable ex )
        {
            txEx = ex;

            // Add whatever exceptions to retry on here
            if ( !(ex instanceof org.neo4j.kernel.DeadlockDetectedException) || ex instanceof  org.neo4j.driver.v1.exceptions.TransientException || ex instanceof org.neo4j.driver.v1.exceptions.ClientException)
            {
                break;
            }
        }

        // Wait so that we don't immediately get into the same deadlock
        if ( i < RETRIES - 1 )
        {
            try
            {
                Thread.sleep( BACKOFF );
            }
            catch ( InterruptedException e )
            {	
            	throw new org.neo4j.graphdb.TransactionFailureException( "Interrupted", e );
            }
        }
    }

    if ( txEx instanceof org.neo4j.graphdb.TransactionFailureException )
    {
        throw ((org.neo4j.graphdb.TransactionFailureException) txEx);
        
    }
    else if ( txEx instanceof Error )
    {
        throw ((Error) txEx);
    }
    else if ( txEx instanceof RuntimeException )
    {
        throw ((RuntimeException) txEx);
    }
    else
    {
        throw new org.neo4j.graphdb.TransactionFailureException( "Failed", txEx );
    }
    // END SNIPPET: retry
}

public Future<SubGraph> requestCharacteristicSubAlignments(int k, double x, boolean edges, ExecutionContextExecutor dispatcher){
	
	@SuppressWarnings("unused")
	int x1 = this.getAlignmentNo();
	SubGraph sg = new SubGraph(this);
	this.setMesaj("oluyor mu=");
	sg.senderNo = this.alignmentNo;
	sg.type = x+" deneme"+" noluyorduuuuu";
	Future<SubGraph> f = future(new Callable<SubGraph>() {
		public SubGraph call() {
			System.out.println(sg.type);
			System.out.println(" tren gelir hoş gelir");
		return sg;
		}
		}, dispatcher);

		return f;
}

public int getAlignmentNo() {
	return alignmentNo;
}

public BenchmarkScores getBenchmarkScores() {
//	this.bs = as.calculateGlobalBenchmarks((Aligner) this);
	return this.bs;
}

public void setBenchmarkScores(BenchmarkScores bs) {
	this.bs = bs;
}

public BenchmarkScores computeAlignmentCharacteristics(){
	return as.calculateGlobalBenchmarks(this);
}


public BenchmarkScores sendAlignmentBenchmarks(){
	return this.bs;
}

public Long sendCharacteristicSubAlignmentMarkedQuery(char c) {
	return null;
}

public String getMesaj() {
	return mesaj;
}

public void setMesaj(String mesaj) {
	this.mesaj = mesaj;
}

public List<BenchmarkScores> getMarkedQueries() {
	return markedQueries;
}

public void setMarkedQueries(List<BenchmarkScores> markedQueries) {
	this.markedQueries = markedQueries;
}

public void addMarkedQueries(Set<BenchmarkScores> newMarkedQueries) {
	for (BenchmarkScores benchmarkScores : this.markedQueries) {
		for (BenchmarkScores bs : newMarkedQueries) {
			if(bs.markedQuery != benchmarkScores.markedQuery)
				this.markedQueries.add(bs);	
		}
	}	
}

public int getNoofCyclesAlignmentUnchanged() {
	return noofCyclesAlignmentUnchanged;
}

public void setNoofCyclesAlignmentUnchanged(int noofCyclesAlignmentUnchanged) {
	this.noofCyclesAlignmentUnchanged = noofCyclesAlignmentUnchanged;
}

@SuppressWarnings("unused")
private boolean contains(Set<Node> s, Node n){
	for (Node node : s) {
		if(node.equals(n))	
		return true;
	}
	return false;
}

@SuppressWarnings("unused")
private boolean noIntersection(Set<Node> aligned, ArrayList<Node> record){
	for (Node node : record) {
		if(aligned.contains(node))
			return false;
		else aligned.add(node);
	}
	return true;
}

private boolean noIntersection2(Set<Node> aligned,ArrayList<Node> record){
	
	for(int i=0;i<record.size();i++)
		 if(aligned.contains(record.get(i)))
			 return false;
		 else aligned.add(record.get(i));
	return true;
}

private boolean addAll(Set<Node> aligned,ArrayList<Node> record){
	
	for(int i=0;i<record.size();i++)
		 if(!aligned.add(record.get(i)))
			 return false;
	return true;
}
//aligned.contains biçimindeki uzun ekleme işlemine eşdeğerdir. Bazen düzgün çalışmıyor?
private int addResultsToAlignment1(ArrayList<ArrayList<Node>> records,Set<Node> aligned){
	Session arta1 = AkkaSystem.driver.session();
	int count = 0;
	try ( org.neo4j.driver.v1.Transaction tx = arta1.beginTransaction() ) {
	for (int i = 0; i<records.size();i++){
		if (noIntersection2(aligned,records.get(i)))
		{
			for (int j = 0; j<records.get(i).size();j=j+2)		
				tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(j).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(j+1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(j).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(j+1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
			aligned.addAll(records.get(i));
			count++;
		}
	 }
	tx.success(); tx.close();
	} catch (Exception e) {
		System.err.println("addResultsToAlignment1::: "+e.getMessage());
		addResultsToAlignment1(records,aligned);
	} finally {arta1.close();}
	return count;
}
// aligned.add biçimindeki uzun ekleme işlemine eşdeğerdir.
private int addResultsToAlignment2(ArrayList<ArrayList<Node>> records,Set<Node> aligned){
	Session arta2 = AkkaSystem.driver.session();
	int count = 0;
	try ( org.neo4j.driver.v1.Transaction tx = arta2.beginTransaction() ){
	for (int i = 0; i<records.size();i++){
		if (addAll(aligned,records.get(i)))
		{
			for (int j = 0; j<records.get(i).size();j=j+2)
				tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(j).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(j+1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(j).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(j+1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
			aligned.addAll(records.get(i));
			count++;
		}
	 }
	tx.success(); tx.close();
	} catch (Exception e) {
		System.err.println("addResultsToAlignment2::: "+e.getMessage());
		addResultsToAlignment2(records,aligned);
	} finally {arta2.close();}
	return count;
}

private int addResultsToAlignment3(ArrayList<ArrayList<Node>> records,Set<Node> aligned){
	Session arta3 = AkkaSystem.driver.session();
	int count = 0;
	
	try ( org.neo4j.driver.v1.Transaction tx = arta3.beginTransaction() ){
		if(!records.isEmpty())
		if(records.get(0).size()==2)
			for (int i = 0; i<records.size();i++){
				if (!aligned.contains(records.get(i).get(0))&&!aligned.contains(records.get(i).get(1)))
				{
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					aligned.add(records.get(i).get(0)); aligned.add(records.get(i).get(1));
					count++;
				}
			}
		else if(records.get(0).size()==4)
		for (int i = 0; i<records.size();i++){
		if (!aligned.contains(records.get(i).get(0))&&!aligned.contains(records.get(i).get(1))&&!aligned.contains(records.get(i).get(2))&&!aligned.contains(records.get(i).get(3)))
		{
			tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
			tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(2).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(3).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
			aligned.add(records.get(i).get(0)); aligned.add(records.get(i).get(1)); aligned.add(records.get(i).get(2)); aligned.add(records.get(i).get(3));
			count++;count++;
		}
	}
		else if(records.get(0).size()==6)
			for (int i = 0; i<records.size();i++){
			if (!aligned.contains(records.get(i).get(0))&&!aligned.contains(records.get(i).get(1))&&!aligned.contains(records.get(i).get(2))&&!aligned.contains(records.get(i).get(3))&&!aligned.contains(records.get(i).get(4))&&!aligned.contains(records.get(i).get(5)))
			{
				tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
				tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(2).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(3).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
				tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(4).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(5).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(4).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(5).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
				aligned.add(records.get(i).get(0)); aligned.add(records.get(i).get(1)); aligned.add(records.get(i).get(2)); aligned.add(records.get(i).get(3)); aligned.add(records.get(i).get(4)); aligned.add(records.get(i).get(5));
				count++;count++;count++;
			}
		}
	tx.success(); tx.close();
	} catch (Exception e) {
		System.err.println("addResultsToAlignment3::: "+e.getMessage()+"\n caused by: "+e.getCause().getMessage());
		addResultsToAlignment3(records,aligned);
	} finally {arta3.close();}
	return count;
}

private int addResultsToAlignment4(ArrayList<ArrayList<Node>> records,Set<Node> aligned){
	Session arta4 = AkkaSystem.driver.session();
	int count = 0;
	try ( org.neo4j.driver.v1.Transaction tx = arta4.beginTransaction() ){
		if(!records.isEmpty())
		if(records.get(0).size()==2)
			for (int i = 0; i<records.size();i++){
				if (aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1)))
				{
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					count++;
				}
			}
		else if(records.get(0).size()==4)
		for (int i = 0; i<records.size();i++){
		if (aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1))&&aligned.add(records.get(i).get(2))&&aligned.add(records.get(i).get(3)))
		{
			tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
			tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(2).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(3).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
			count++;count++;
		}
	}
		else if(records.get(0).size()==6)
			for (int i = 0; i<records.size();i++){
			if (aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1))&&aligned.add(records.get(i).get(2))&&aligned.add(records.get(i).get(3))&&aligned.add(records.get(i).get(4))&&aligned.add(records.get(i).get(5)))
			{
				tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
				tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(2).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(3).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
				tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(4).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(5).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(4).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(5).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
				count++;count++;count++;
			}
		}
	tx.success(); tx.close();
	} catch (Exception e) {
		System.err.println("addResultsToAlignment4::: "+e.getMessage());
		addResultsToAlignment4(records,aligned);
	} finally {arta4.close();}
	return count;
}

@SuppressWarnings("null")
private int addResultsToAlignment(ArrayList<ArrayList<Node>> records,Set<Node> aligned, char mode){
	TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
	tm.failure(new Throwable("Herkesin tuttuğu kendine"));
	
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
	int added = template.with(AkkaSystem.graphDb).execute( transaction -> {
		switch (mode) {
		case '1':
			return addResultsToAlignment1(records, aligned);
		case '2':
			return addResultsToAlignment2(records, aligned);
		case '3':
			return addResultsToAlignment3(records, aligned);
		case '4':
			return addResultsToAlignment4(records, aligned);
		default:
			System.out.println("Unexpected mode!");
			break;
		}
		return 0;
	} );
	if(added<=0) {
		noofCyclesAlignmentUnchanged++;
		if(noofCyclesAlignmentUnchanged>20)
			System.err.println("Alignment "+this.alignmentNo+" did not improve itself for "+noofCyclesAlignmentUnchanged+" cycles.");
		} else {
			try(FileWriter fw = new FileWriter("add"+this.alignmentNo+".txt", true);
				    BufferedWriter bw = new BufferedWriter(fw);
				    PrintWriter out = new PrintWriter(bw))
				{
					out.println("["+noofCyclesAlignmentUnchanged+" | SelfImprovement | "+ZonedDateTime.now()+"]: "+added+" mappings are added to aligner "+this.alignmentNo+" with self improvement");
				} catch (IOException e) {
				    //exception handling left as an exercise for the reader
				}
			noofCyclesAlignmentUnchanged = 0;			
		}		
	return added;
//Lock lock = new ReentrantLock();
//	lock.lock();
//	try {
//		try {
//			switch (mode) {
//			case '1':
//				addResultsToAlignment1(records, aligned);
//				break;
//			case '2':
//				addResultsToAlignment2(records, aligned);
//				break;
//			case '3':
//				addResultsToAlignment3(records, aligned);
//				break;
//			case '4':
//				addResultsToAlignment4(records, aligned);
//				break;
//			default:
//				System.out.println("Unexpected mode!");
//				break;
//			}
//		} catch (Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} 
//	} finally {
//		lock.unlock();
//	}
}

public void writeAlignmentToDisk(String alignmentName){
	TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
	tm.failure(new Throwable("Herkesin tuttuğu kendine"));
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
	int added = template.with(AkkaSystem.graphDb).execute( transaction -> {
		Session writeSession = AkkaSystem.driver.session();
	
	StatementResult result;
	String n = null;
	String m = null;
	int count = 0;
	try ( org.neo4j.driver.v1.Transaction tx = writeSession.beginTransaction(); 
			BufferedWriter bw = new BufferedWriter(new FileWriter(alignmentName)))
    {
		result = tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where r.alignmentNumber = '"+ alignmentNo +"' return n.proteinName,m.proteinName");
		while(result.hasNext()){
			Record row = result.next();
			
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null)
					switch (column.getKey()) {
					case "n.proteinName":
						n = row.get( column.getKey() ).asString();
						break;
					case "m.proteinName":
						m = row.get( column.getKey() ).asString();
						break;
					default:
						System.out.println("Unexpected column");
						break;
					}
			}
			bw.write(n+" "+m);
			bw.newLine();
			count++;
		}
		tx.success(); tx.close();
		bw.close();
    } catch (Exception e){
    	  e.printStackTrace();
      } finally {writeSession.close();}
    return count;
} );
	System.out.println(added+" alignments are written to "+alignmentName+" file");
}

public void writeAlignmentToDisk(String alignmentName, String markedQuery){
	
	TransactionTemplate.Monitor tm = new TransactionTemplate.Monitor.Adapter();
	tm.failure(new Throwable("Herkesin tuttuğu kendine"));
	TransactionTemplate template = new TransactionTemplate(  ).retries( 1000 ).backoff( 5, TimeUnit.SECONDS ).monitor(tm);
	int added = template.with(AkkaSystem.graphDb).execute( transaction -> {
		Session writeSession = AkkaSystem.driver.session();
	
	StatementResult result;
	String n = null;
	String m = null;
	int count = 0;
	try ( org.neo4j.driver.v1.Transaction tx = writeSession.beginTransaction(); 
			BufferedWriter bw = new BufferedWriter(new FileWriter(alignmentName)))
    {
		result = tx.run("match (n:Organism2)-[r:ALIGNS]->(m:Organism1) where r.alignmentNumber = '"+ alignmentNo +"' and ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') and ANY(x IN m.markedQuery WHERE x = '"+markedQuery+"') return n.proteinName,m.proteinName");
		while(result.hasNext()){
			Record row = result.next();
			
			for ( Entry<String,Object> column : row.asMap().entrySet() ){
				if(column.getValue()!=null)
					switch (column.getKey()) {
					case "n.proteinName":
						n = row.get( column.getKey() ).asString();
						break;
					case "m.proteinName":
						m = row.get( column.getKey() ).asString();
						break;
					default:
						System.out.println("Unexpected column");
						break;
					}
			}
			bw.write(n+" "+m);
			bw.newLine();
			count++;
		}
		tx.success(); tx.close();
		bw.close();
    } catch (Exception e){
    	  e.printStackTrace();
      } finally {writeSession.close();}
    return count;
} );
	System.out.println(added+" alignments are written to "+alignmentName+" file");
	}

@Override
public void onReceive(Object message, ActorRef sender) {
	// TODO Auto-generated method stub

	if(message instanceof BenchmarkScores) {
		BenchmarkScores messageScores = (BenchmarkScores) message;
		
	if(messageScores.isParetoDominantThan(this.bs)&&messageScores.getAlignmentNo()!=this.alignmentNo)
	{
		this.addAlignment(messageScores.alignmentNo);
		
	} else 
		if(messageScores.alignmentNo != this.alignmentNo)
	{
	BenchmarkScores diff = messageScores.difference(this.bs," resultFile.txt");

	// aşağıdakilerin hepsini değil bir döngü içerisinde bazılarını geri göndersem daha mı iyi olur?
	if(diff.getEC()<0 && diff.getICS()<0 && diff.getS3() <0 && diff.getBitScore()<0 && diff.getGOC()<0 ) {
		Future<SubGraph> f0 = this.currentAlignment(AkkaSystem.system2.dispatcher());
		akka.pattern.Patterns.pipe(f0, AkkaSystem.system2.dispatcher()).to(sender);
		f0.onSuccess(new PrintResult<SubGraph>(), AkkaSystem.system2.dispatcher());
		f0.onFailure(new OnFailure() {
			public void onFailure(Throwable failure) {
				System.err.println("olmaz birtanem, hiçbiri olmaz");
			}
		},AkkaSystem.system2.dispatcher());
	} else {
		String choice = this.bs.listOfBetterBenchmarks(messageScores).get((int)(this.bs.listOfBetterBenchmarks(messageScores).size()*Math.random()));
		if(choice.equals("EC")) 
		{
			Future<SubGraph> f1 = this.subGraphWithAlignedEdges(AkkaSystem.system2.dispatcher());
			akka.pattern.Patterns.pipe(f1, AkkaSystem.system2.dispatcher()).to(sender);
			f1.onSuccess(new PrintResult<SubGraph>(), AkkaSystem.system2.dispatcher());
			f1.onFailure(new OnFailure() {
				public void onFailure(Throwable failure) {
					System.err.println("olmaz birtanem, EC olmaz");
				}
			},AkkaSystem.system2.dispatcher());
		}
		if(choice.equals("ICS")) {
		//betweenness, harmonic, pagerank and closeness
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
				
		Future<SubGraph> fs = this.subGraphWithTopAlignedPowerNodes(100, centralityType, AkkaSystem.system2.dispatcher());
		akka.pattern.Patterns.pipe(fs, AkkaSystem.system2.dispatcher()).to(sender);
		fs.onSuccess(new PrintResult<SubGraph>(), AkkaSystem.system2.dispatcher());
		fs.onFailure(new OnFailure() {
			public void onFailure(Throwable failure) {
				System.err.println("olmaz birtanem, ICS olmaz");
				failure.printStackTrace();
			}
		},AkkaSystem.system2.dispatcher());
	}
	
		if(choice.equals("S3")) {
			Future<SubGraph> fs;
			if(Math.random() < 0.8)
				fs = this.subGraphWithTopAlignedPowerNodes(100, "power", AkkaSystem.system2.dispatcher());
			else
				fs = this.subGraphWithStrongInducedEdges(AkkaSystem.system2.dispatcher(), 0, 0, 0);
		akka.pattern.Patterns.pipe(fs, AkkaSystem.system2.dispatcher()).to(sender);
		fs.onSuccess(new PrintResult<SubGraph>(), AkkaSystem.system2.dispatcher());
		fs.onFailure(new OnFailure() {
			public void onFailure(Throwable failure) {
				System.err.println("olmaz birtanem, S3 olmaz");
				failure.printStackTrace();
			}
		},AkkaSystem.system2.dispatcher());
	}
	
		if(choice.equals("BS"))
		{
		Future<SubGraph> f2 = this.subGraphWithXBitScoreSimilarity(messageScores.getBitScore()/(messageScores.getSize()),AkkaSystem.system2.dispatcher());
		akka.pattern.Patterns.pipe(f2, AkkaSystem.system2.dispatcher()).to(sender);
		f2.onSuccess(new PrintResult<SubGraph>(), AkkaSystem.system2.dispatcher());
		f2.onFailure(new OnFailure() {
			public void onFailure(Throwable failure) {
				System.err.println("olmaz birtanem, BitScore olmaz");
			}
		},AkkaSystem.system2.dispatcher());
		}
		if(choice.equals("GOC")){
		Future<SubGraph> f3 = this.subGraphWithKGOTerms((int)Math.ceil(messageScores.getGOC()/(messageScores.getSize())), AkkaSystem.system2.dispatcher());
		akka.pattern.Patterns.pipe(f3, AkkaSystem.system2.dispatcher()).to(sender);
		f3.onSuccess(new PrintResult<SubGraph>(), AkkaSystem.system2.dispatcher());
		f3.onFailure(new OnFailure() {
			public void onFailure(Throwable failure) {
				System.err.println("olmaz birtanem, GOC olmaz");
			}
		},AkkaSystem.system2.dispatcher());
	}
	}
	
	
	}
	}
	if(message instanceof SubGraph&&this.alignmentNo!=((SubGraph) message).senderNo)
		{
			System.out.println(this.alignmentNo+" numaralı hizalayıcı "+((SubGraph) message).senderNo+" numaralı hizalayıcıdan SubGraph biçiminde ve "+((SubGraph) message).aligns.size()+" eşleşme içeren bir mesaj aldı.");
			this.addAlignment((SubGraph) message);		
		}

	if(message instanceof Long) {
		System.out.println(this.alignmentNo+" numaralı hizalayıcı tarafından "+(Long) message+" numarası ile işaretli bir alt çizge sorgusu mesaj olarak alındı.");
		this.addAlignment(((Long) message).longValue(),1);
	}
//	if(message instanceof BenchmarkScores)
//		sender.tell(this.requestCharacteristicSubAlignments(0, 4.0, true, this.as.system2.dispatcher()),this.as.typed.getActorRefFor(TypedActor.<Aligner>self()));
//		//akka.pattern.Patterns.pipe(this.requestCharacteristicSubAlignments(0, 0.0, true, this.as.system2.dispatcher()), this.as.system2.dispatcher()).to(sender);
	try {
//		if(message instanceof String)
//			System.out.println(this.alignmentNo+" numaralı aktörün String mesajı  geldi. Mesaj İçeriği: -> "+(String) message);
//		if(message instanceof Long)
//			System.out.println(this.alignmentNo+" numaralı aktörün Long mesajı  geldi. Mesaj İçeriği: -> "+(Long) message);
//		if(message instanceof BenchmarkScores)
//			{
//			System.out.println(this.alignmentNo+" numaralı aktörün Benchmark mesajı  geldi. Mesaj İçeriği: -> "+message);
//			}
//		if(message instanceof Future<?>)
//		{
//
//			System.out.println(this.alignmentNo+" numaralı aktörün Future mesajı  geldi. Mesaj İçeriği: -> "+message);
//			if(((Future<?>) message).isCompleted())
//				System.out.println("tamamlanmış mesaj: "+((Future<?>) message).value());
////				result = Await.result((Future) message, new Timeout(Duration.create(1, "seconds")).duration());
//			
//		}
//		if(message instanceof SubGraph)
//		{
//		System.out.println(this.alignmentNo+" numaralı aktörün SubGraph mesajı  geldi. Gönderici numerosu: "+((SubGraph) message).senderNo+". Mesaj İçeriği: -> "+message);
//		if(((SubGraph) message).senderNo==this.alignmentNo) {
//			if(((SubGraph) message).type.equals("deneme"))
//				{
//				System.out.println(this.alignmentNo+" deniyor muydu");	
//				}
//		}
//		}
			} catch (Exception e) {
		// TODO Auto-generated catch block
		System.out.println("onReceive::: "+e.getMessage());
	}
	
}
	
private static Future<SubGraph> future(Callable<SubGraph> callable, ExecutionContextExecutor dispatcher) {
	// TODO Auto-generated method stub
	try {
		return Futures.future(callable,dispatcher);
	} catch (Exception e) {
		// TODO Auto-generated catch block
		System.err.println("fevzi");
		SubGraph hezimet = new SubGraph(new AkkaSystem(3),TypedActor.<AlignerImpl>self(),(long) 5);
		hezimet.type = "hep kazanamayız!!!";
		return Futures.successful(hezimet);
	}
}

private static Future<Long> gelecek(Callable<Long> callable, ExecutionContextExecutor dispatcher) {
	// TODO Auto-generated method stub
	try {
		return Futures.future(callable,dispatcher);
	} catch (Exception e) {
		// TODO Auto-generated catch block
		System.err.println("fevzi");
		Long hezimet = 0L;
		return Futures.successful(hezimet);
	}
}

}
