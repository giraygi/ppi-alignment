package typed.ppi;

import org.neo4j.ogm.annotation.EndNode;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Property;
import org.neo4j.ogm.annotation.Relationship;
import org.neo4j.ogm.annotation.RelationshipEntity;
import org.neo4j.ogm.annotation.StartNode;

@NodeEntity
public class Organism1 {
	
	Long id;
	
	String proteinName;
	String[] annotations;
	String[] marked;
	String[] markedQuery;
	
	long labelPropagation;
	long louvain;
	long lccs;
	
	double closeness;
	double betweenness;
	double pagerank;
	double harmonic;
	
	int power2;
	int power3;
	int power4;
	
	@Relationship(type="INTERACTS_1") private Interacts_1 organism1;

}


@NodeEntity
class Organism2 {
	
	Long id;
	@Property(name="proteinName")
	String proteinName;
	String[] annotations;
	String[] marked;
	String[] markedQuery;
	
	long labelPropagation;
	long louvain;
	long lccs;
	
	double closeness;
	double betweenness;
	double pagerank;
	double harmonic;
	
	int power2;
	int power3;
	int power4;
	
	@Relationship(type="INTERACTS_2") private Interacts_2 organism2;

}

@RelationshipEntity(type = "INTERACTS_1")
class Interacts_1 {
	
	@Id @GeneratedValue   private Long relationshipId;
    @Property  private 	String[] marked;
    @Property  private String[] markedQuery;
    @StartNode private Organism1 start;
    @EndNode   private Organism1 end;
	
}

@RelationshipEntity(type = "INTERACTS_2")
class Interacts_2 {
	
	@Id @GeneratedValue   private Long relationshipId;
    @Property  private 	String[] marked;
    @Property  private String[] markedQuery;
    @StartNode private Organism1 start;
    @EndNode   private Organism1 end;
	
}

class Similarity{
	
	@Id @GeneratedValue   private Long relationshipId;
	private double similarity;
	 @StartNode private Organism1 start;
	 @EndNode   private Organism2 end;
	
}

class Aligns {
	
	@Id @GeneratedValue   private Long relationshipId;
	private String alignmentNo;
	private String alignmentIndex;
	 @StartNode private Organism2 start;
	 @EndNode   private Organism1 end;
	
}

