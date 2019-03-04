
package typed.ppi;
import java.util.ArrayList;

import org.neo4j.driver.v1.types.Node;
import org.neo4j.driver.v1.types.Relationship;

public class GraphRecord {
	
	ArrayList<Node> nodes1 = new ArrayList<Node>();
	ArrayList<Node> nodes2 = new ArrayList<Node>();
	ArrayList<Relationship> interactions1 = new ArrayList<Relationship>();
	ArrayList<Relationship> interactions2 = new ArrayList<Relationship>();
	ArrayList<Relationship> aligns = new ArrayList<Relationship>();
	ArrayList<Relationship> similarity = new ArrayList<Relationship>();

}
