
package typed.ppi;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.types.Node; 
import org.neo4j.driver.v1.types.Relationship;


public class SubGraph implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	Set<Node> nodes1 = new HashSet<Node>();
	Set<Node> nodes2 = new HashSet<Node>();
	Set<Relationship> interactions1 = new HashSet<Relationship>();
	Set<Relationship> interactions2 = new HashSet<Relationship>();
	Set<Relationship> aligns = new HashSet<Relationship>();
	Set<Relationship> similarity = new HashSet<Relationship>();
	FileWriter fw;
	BufferedWriter bw;
	Session  session;
	Session innerSession;
	BenchmarkScores bs;
	int senderNo;
	String type;
	
	public SubGraph() {
		session = AkkaSystem.driver.session();
		innerSession = AkkaSystem.driver.session();
	}
	
	public SubGraph(AlignerImpl a){
		session = a.session;
		innerSession = a.innerSession;
		bs = a.getBenchmarkScores();
		senderNo = a.alignmentNo;
	}
	
	public SubGraph(AkkaSystem p,AlignerImpl a,Long markedQuery){
		session = p.session;
		bs = a.getBenchmarkScores();
		senderNo = a.alignmentNo;
		init(markedQuery);
	}
	
	public SubGraph init(Long markedQuery) {
		StatementResult result;
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
			result = tx.run("(n:Organism1) where ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') return n");
			while(result.hasNext()) {
				Record row = result.next();
				 nodes1.add(row.get(0).asNode());
			}
			result = tx.run("(n:Organism2) where ANY(x IN n.markedQuery WHERE x = '"+markedQuery+"') return n");
			while(result.hasNext()) {
				Record row = result.next();
				 nodes2.add(row.get(0).asNode());
			}
			result = tx.run("()-[r:ALIGNS]-() where ANY(x IN r.markedQuery WHERE x = '"+markedQuery+"') return r");
			while(result.hasNext()) {
				Record row = result.next();
				 aligns.add(row.get(0).asRelationship());
			}
			result = tx.run("()-[i:INTERACTS_1]-() where ANY(x IN i.markedQuery WHERE x = '"+markedQuery+"') return i");
			while(result.hasNext()) {
				Record row = result.next();
				 interactions1.add(row.get(0).asRelationship());
			}
			result = tx.run("()-[i:INTERACTS_2]-() where ANY(x IN i.markedQuery WHERE x = '"+markedQuery+"') return i");
			while(result.hasNext()) {
				Record row = result.next();
				 interactions2.add(row.get(0).asRelationship());
			}
			result = tx.run("()-[s:SIMILARITY]-() where ANY(x IN s.markedQuery WHERE x = '"+markedQuery+"') return s");
			while(result.hasNext()) {
				Record row = result.next();
				 similarity.add(row.get(0).asRelationship());
			}
			
			tx.success();
		} catch (Exception e){
			e.printStackTrace();
		}
		
		return null;}
	
	public SubGraph intersection(SubGraph s){
		s.nodes1.retainAll(this.nodes1);
		s.nodes2.retainAll(this.nodes2);
		s.interactions1.retainAll(this.interactions1);
		s.interactions2.retainAll(this.interactions2);
		s.aligns.retainAll(this.aligns);
		s.similarity.retainAll(this.similarity);
		return s;
	}
	
	public SubGraph union (SubGraph s){
		s.nodes1.addAll(this.nodes1);
		s.nodes2.addAll(this.nodes2);
		s.interactions1.addAll(this.interactions1);
		s.interactions2.addAll(this.interactions2);
		s.aligns.addAll(this.aligns);
		s.similarity.addAll(this.similarity);
		return s;
	}
	// Parametre olan alt �izgenin farkl� elemanlar�n� d�nd�r�r
	public /*synchronized*/ SubGraph difference (SubGraph s){
		for (Node node : this.nodes1) {
			s.nodes1.removeIf((Node n)->n.id() == node.id());
		}
		
		for (Node node : this.nodes2) {
			s.nodes2.removeIf((Node n)->n.id() == node.id());
		}
		
		for (Relationship relationship : this.aligns) {
			s.aligns.removeIf((Relationship r)->r.id() == relationship.id());
			s.aligns.removeIf((Relationship r)->r.endNodeId() == relationship.endNodeId());
			s.aligns.removeIf((Relationship r)->r.startNodeId() == relationship.startNodeId());
		}
		
		for (Relationship relationship : this.similarity) {
			s.similarity.removeIf((Relationship r)->r.id() == relationship.id());
		}
		
		for (Relationship relationship : this.interactions1) {
			s.interactions1.removeIf((Relationship r)->r.id() == relationship.id());
		}
		
		for (Relationship relationship : this.interactions2) {
			s.interactions2.removeIf((Relationship r)->r.id() == relationship.id());
		}
		s.type = "Difference of "+s.type+" from "+this.type;
		return s;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();	
		builder.append("SubGraph nodes1= ");
		 for (Node n : this.nodes1) {
		    builder.append(n.toString().replaceAll("\n", "")+" ");
		}
		builder.append("\n nodes2= ");
		for (Node n : this.nodes2) {
		    builder.append(n.toString().replaceAll("\n", "")+" ");
		}
		builder.append("\n interactions1= ");
		for (Relationship r : this.interactions1) {
		    builder.append(r.toString().replaceAll("\n", "")+" ");
		}
		builder.append("\n interactions2= ");
		for (Relationship r : this.interactions2) {
		    builder.append(r.toString().replaceAll("\n", "")+" ");
		}
		builder.append("\n alignments= ");
		for (Relationship r : this.aligns) {
		    builder.append(r.toString().replaceAll("\n", "")+" ");
		}
		builder.append("\n similarities= ");
		for (Relationship r : this.similarity) {
		    builder.append(r.toString().replaceAll("\n", "")+" ");
		}
		builder.append("\n sender: "+this.senderNo);
		builder.append("\n type: "+this.type);
		return builder.toString().replaceAll("\n", "");
	}
	
	//Neo4j Match ()-[r]-() Where ID(r)=1 ya da (n:Organism1) WHERE ID(s) = 65110 set bilmemne daha do�ru diyo
	
	public void markSubGraph(int queryNumber){
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() )
		{
		   // set n.markedQuery = n.markedQuery + '"+queryNumber+"' 
			for (Node n : this.nodes1) {	
				tx.run( "start n=node("+n.id()+") where not ANY(x IN n.markedQuery WHERE x = '"+queryNumber+"') set n.markedQuery = n.markedQuery + '"+queryNumber+"' return (n)" );
			}
			for (Node n : this.nodes2) {	
				tx.run( "start n=node("+n.id()+") where not ANY(x IN n.markedQuery WHERE x = '"+queryNumber+"') set n.markedQuery = n.markedQuery + '"+queryNumber+"' return (n)" );
			} 
			for (Relationship r : this.interactions1) {
				tx.run( "start r=rel("+r.id()+") where not ANY(x IN r.markedQuery WHERE x = '"+queryNumber+"') set r.markedQuery = r.markedQuery + '"+queryNumber+"' return(r)" );
			}
			for (Relationship r : this.interactions2) {
				tx.run( "start r=rel("+r.id()+") where not ANY(x IN r.markedQuery WHERE x = '"+queryNumber+"') set r.markedQuery = r.markedQuery + '"+queryNumber+"' return(r)" );
			}
			for (Relationship r : this.aligns) {
				tx.run( "start r=rel("+r.id()+") where not ANY(x IN r.markedQuery WHERE x = '"+queryNumber+"') set r.markedQuery = r.markedQuery + '"+queryNumber+"' return(r)" );
			}
			for (Relationship r : this.similarity) {
				tx.run( "start r=rel("+r.id()+") where not ANY(x IN r.markedQuery WHERE x = '"+queryNumber+"') set r.markedQuery = r.markedQuery + '"+queryNumber+"' return(r)" );
			}	
			
			tx.success();
		} catch (Exception e){
			e.printStackTrace();
		}
		
	}
	
	public void unmarkSubGraph(int queryNumber) {
		
	}
	
	public void writeAlignmentsToDisk(String alignmentName){		
		StatementResult result;
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction(); BufferedWriter bw = new BufferedWriter(new FileWriter(alignmentName)) )
		{
		
		for (Relationship r : this.aligns) {
			result = tx.run("match (s:Organism2)-[:ALIGNS]->(e:Organism1) where ID(s)="+r.startNodeId()+" and ID(e)="+r.endNodeId()+" return s.proteinName,e.proteinName");
				Record record = result.single();
				bw.write(record.get("s.proteinName").asString()+" "+record.get("e.proteinName").asString());
				bw.newLine();
		}
		bw.close();
		tx.success();
	} catch (Exception e){
		e.printStackTrace();
	}
		}
	
	public void writeSimilaritiesToDisk(String alignmentName){
		StatementResult result;
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction(); BufferedWriter bw = new BufferedWriter(new FileWriter(alignmentName)) )
		{
		
		for (Relationship r : this.aligns) {
			result = tx.run("match (e:Organism2)<-[r:SIMILARITY]-(s:Organism1) where ID(s)="+r.startNodeId()+" and ID(e)="+r.endNodeId()+" return s.proteinName,e.proteinName,r.similarity");
				Record record = result.single();
				bw.write(record.get("s.proteinName").asString()+" "+record.get("e.proteinName").asString()+" "+record.get("r.similarity").asDouble());
				bw.newLine();
		}
		bw.close();
		tx.success();
	} catch (Exception e){
		e.printStackTrace();
	}	
		}
	
	public void writeNodesToDisk(String alignmentName, boolean organism1){
		StatementResult result;
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction(); BufferedWriter bw = new BufferedWriter(new FileWriter(alignmentName)) )
		{
		if(organism1)
		for (Node n : this.nodes1) {
			result = tx.run("match (n:Organism1) where ID(n)="+n.id()+" return n.proteinName,n.annotations");
				Record record = result.single();
				bw.write(record.get("n.proteinName").asString()+" "+record.get("n.annotations").asString());
				bw.newLine();
		}
		else 
			for (Node n : this.nodes2) {
				result = tx.run("match (n:Organism2) where ID(n)="+n.id()+" return n.proteinName,n.annotations");
					Record record = result.single();
					bw.write(record.get("n.proteinName").asString()+" "+record.get("n.annotations").asString());
					bw.newLine();
			}
		bw.close();
		tx.success();
	} catch (Exception e){
		e.printStackTrace();
	}	
		}
	
	public void writeInteractionsToDisk(String alignmentName, boolean organism1){
		StatementResult result;
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction(); BufferedWriter bw = new BufferedWriter(new FileWriter(alignmentName)) )
		{
		if(organism1)
		for (Relationship r : this.interactions1) {
			result = tx.run("match (s:Organism1)-[:INTERACTS_1]->(e:Organism1) where ID(s)="+r.startNodeId()+" and ID(e)="+r.endNodeId()+" return s.proteinName,e.proteinName");
				Record record = result.single();
				bw.write(record.get("s.proteinName").asString()+" "+record.get("e.proteinName").asString());
				bw.newLine();
		}
		else
			for (Relationship r : this.interactions2) {
				result = tx.run("match (s:Organism2)-[:INTERACTS_2]->(e:Organism2) where ID(s)="+r.startNodeId()+" and ID(e)="+r.endNodeId()+" return s.proteinName,e.proteinName");
					Record record = result.single();
					bw.write(record.get("s.proteinName").asString()+" "+record.get("e.proteinName").asString());
					bw.newLine();
			}
		bw.close();
		tx.success();
	} catch (Exception e){
		e.printStackTrace();
	}
		
	}
	
}
