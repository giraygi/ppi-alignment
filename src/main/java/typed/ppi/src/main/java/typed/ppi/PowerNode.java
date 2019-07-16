
package typed.ppi;

import org.neo4j.driver.v1.types.Node;

public class PowerNode {
	Node node;
	int connections;
	
	public PowerNode(Node node, int connections) {
		super();
		this.node = node;
		this.connections = connections;
	}
	
	
}
