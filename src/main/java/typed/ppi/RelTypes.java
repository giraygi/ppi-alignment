package typed.ppi;
import org.neo4j.graphdb.RelationshipType;


enum RelTypes implements RelationshipType
{
    INTERACTS_1,
    INTERACTS_2,
    ALIGNS,
    SIMILARITY
}