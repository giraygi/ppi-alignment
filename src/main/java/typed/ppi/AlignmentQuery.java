
package typed.ppi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Map.Entry;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.driver.v1.*;

public class AlignmentQuery {
	
	Session session;
	Session innerSession;
	int maxCommonAnnotations = 0;
	double maxSimilarity = 0.0;
	int alignmentNo;
	
	public AlignmentQuery(Session session, Session innerSession, int maxCommonAnnotations, double maxSimilarity,
			int alignmentNo) {
		super();
		this.session = session;
		this.innerSession = innerSession;
		this.maxCommonAnnotations = maxCommonAnnotations;
		this.maxSimilarity = maxSimilarity;
		this.alignmentNo = alignmentNo;
	}
	
	public void increaseBitScoreAndGOC(int minCommonAnnotations){
		System.out.println("increaseBitScoreAndGOC");
		StatementResult result;
		int countRows = 0;
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() ) {		
		markUnalignedNodes();
	ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
	ArrayList<Node> record = new ArrayList<Node>();
	result = tx.run("match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)<-[t:SIMILARITY]-(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[s:SIMILARITY]->(o) where (p.marked=true and n.marked=true and m.marked=true and o.marked=true) and length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" and length(FILTER(x in o.annotations WHERE x in m.annotations)) >="+minCommonAnnotations+" return o,m,p,n order by t.similarity+s.similarity desc");
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
	System.out.println("Number of rows in query:"+countRows);
	Set<Node> aligned = new HashSet<Node>();

	for (int i = 0; i<records.size();i++){
		if (aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1))&&aligned.add(records.get(i).get(2))&&aligned.add(records.get(i).get(3)))
		{
			tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
			tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(2).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(3).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
		}
	}

	tx.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked = false )");

	tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = true");
	tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = true");

	result = tx.run("match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)<-[t:SIMILARITY]-(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)<-[s:ALIGNS]-(o) where (p.marked=true and n.marked=true) and length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" return p,n order by t.similarity desc, length(FILTER(x in p.annotations WHERE x in n.annotations)) desc");
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
		records.add(new ArrayList<Node>(record));
		countRows++;
		}
	System.out.println("Number of rows in query:"+countRows);
	for (int i = 0; i<records.size(); i++){
		if (aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1)))
		{
			tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
		}
	}

	tx.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked = false )");

	tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = true");
	tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = true");

	result = tx.run("match (p:Organism2)<-[t:SIMILARITY]-(n:Organism1) where (p.marked=true and n.marked=true) and length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" return p,n order by t.similarity desc, length(FILTER(x in p.annotations WHERE x in n.annotations)) desc");
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
		records.add(new ArrayList<Node>(record));
		countRows++;
		}
	System.out.println("Number of rows in query deneme:"+countRows);
	for (int i = 0; i<records.size(); i++){
		if (aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1)))
		{
			tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
			//System.out.println(records.get(i).get(0).get("proteinName").asString()+" "+records.get(i).get(1).get("proteinName").asString() );
		}
	}
			
			tx.success();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		unmarkAllNodes();
	}

	public void increaseBitScoreAndGOC2(int minCommonAnnotations){
		System.out.println("increaseBitScoreAndGOC");
		StatementResult result;
		int countRows = 0;
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() ) {
			markUnalignedNodes();
			
	ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
	ArrayList<Node> record = new ArrayList<Node>();
	result = tx.run("match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)<-[t:SIMILARITY]-(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[s:SIMILARITY]->(o) where (p.marked=true and n.marked=true and m.marked=true and o.marked=true) and length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" and length(FILTER(x in o.annotations WHERE x in m.annotations)) >="+minCommonAnnotations+" return o,m,p,n order by t.similarity+s.similarity desc");
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
	System.out.println("Number of rows in query:"+countRows);
	Set<Node> aligned = new HashSet<Node>();

	for (int i = 0; i<records.size();i++){
		if (!aligned.contains(records.get(i).get(0))&&!aligned.contains(records.get(i).get(1))&&!aligned.contains(records.get(i).get(2))&&!aligned.contains(records.get(i).get(3)))
		{
			tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
			tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(2).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(3).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
			aligned.add(records.get(i).get(0)); aligned.add(records.get(i).get(1)); aligned.add(records.get(i).get(2)); aligned.add(records.get(i).get(3));
			
		}
	}

	tx.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked = false )");

	tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = true");
	tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = true");

	result = tx.run("match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)<-[t:SIMILARITY]-(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)<-[s:ALIGNS]-(o) where (p.marked=true and n.marked=true) and length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" return p,n order by t.similarity desc, length(FILTER(x in p.annotations WHERE x in n.annotations)) desc");
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
		records.add(new ArrayList<Node>(record));
		countRows++;
		}
	System.out.println("Number of rows in query:"+countRows);
	for (int i = 0; i<records.size(); i++){
		if (!aligned.contains(records.get(i).get(0))&&!aligned.contains(records.get(i).get(1)))
		{
			tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
			aligned.add(records.get(i).get(0)); aligned.add(records.get(i).get(1));
		}
	}

	tx.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked = false )");

	tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = true");
	tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = true");

	result = tx.run("match (p:Organism2)<-[t:SIMILARITY]-(n:Organism1) where (p.marked=true and n.marked=true) and length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" return p,n order by t.similarity desc, length(FILTER(x in p.annotations WHERE x in n.annotations)) desc");
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
		records.add(new ArrayList<Node>(record));
		countRows++;
		}
	System.out.println("Number of rows in query:"+countRows);
	for (int i = 0; i<records.size(); i++){
		if (!aligned.contains(records.get(i).get(0))&&!aligned.contains(records.get(i).get(1)))
		{
			tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
			aligned.add(records.get(i).get(0)); aligned.add(records.get(i).get(1));
		}
	}
			
			tx.success();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		unmarkAllNodes();
	}
	// Yava� �al���yor
	public void increaseGOCAndBitScore(int minCommonAnnotations){
		System.out.println("increaseGOCAndBitScore");	
		StatementResult result;
		int countRows = 0;
			
			try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() ) {
				markUnalignedNodes();
				ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
				ArrayList<Node> record = new ArrayList<Node>();

				result = tx.run("match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)<-[t:SIMILARITY]-(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[s:SIMILARITY]->(o) where (p.marked=true and n.marked=true and m.marked=true and o.marked=true) return o,m,p,n order by length(FILTER(x in p.annotations WHERE x in n.annotations))+length(FILTER(x in o.annotations WHERE x in m.annotations)) desc");
		// baya yawa� result = tx.run("match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1) where (p.marked=true and n.marked=true and m.marked=true and o.marked=true) and length(FILTER(x in p.annotations WHERE x in n.annotations))+length(FILTER(x in o.annotations WHERE x in m.annotations)) >="+minCommonAnnotations+" return o,m,p,n order by length(FILTER(x in p.annotations WHERE x in n.annotations))+length(FILTER(x in o.annotations WHERE x in m.annotations)) desc limit 500");
		// optimizasyon denemesi result = tx.run("match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)<-[:SIMILARITY]-(n:Organism1)-[r:INTERACTS_1]-(m:Organism1) where (p.marked=true and n.marked=true and m.marked=true and o.marked=true) and length(p.annotations) >=5 and length(o.annotations) >=5 and length(m.annotations) >=5 and length(n.annotations) >=5 return o,m,p,n order by length(FILTER(x in p.annotations WHERE x in n.annotations)) desc, length(FILTER(x in o.annotations WHERE x in m.annotations)) desc limit 500");
		//System.out.println("Number of records in query: "+result.list().size());
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
				System.out.println("Number of rows in query:"+countRows);
				Set<Node> aligned = new HashSet<Node>();

		for (int i = 0; i<records.size();i++){
			if (aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1))&&aligned.add(records.get(i).get(2))&&aligned.add(records.get(i).get(3)))
			{
				tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
				tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(2).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(3).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
			}
		}
		tx.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked = false )");
		tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = true");
		tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = true");
		result = tx.run("match (p:Organism2)<-[t:SIMILARITY]-(n:Organism1) where (p.marked=true and n.marked=true) and length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" return p,n order by length(FILTER(x in p.annotations WHERE x in n.annotations)) desc");
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
			records.add(new ArrayList<Node>(record));
			countRows++;
			}
		System.out.println("Number of rows in query:"+countRows);
		for (int i = 0; i<records.size(); i++){
			if (aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1)))
			{
				tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
			}
		}

		tx.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked = false )");
		tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = true");
		tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = true");
		result = tx.run("match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[t:ALIGNS]->(n:Organism1)-[r:INTERACTS_1]-(m:Organism1) where (m.marked=true and o.marked=true) and length(FILTER(x in o.annotations WHERE x in m.annotations)) >="+minCommonAnnotations+" return o,m order by length(FILTER(x in o.annotations WHERE x in m.annotations)) desc");
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
			records.add(new ArrayList<Node>(record));
			countRows++;
			}
		System.out.println("Number of rows in query:"+countRows);
		for (int i = 0; i<records.size(); i++){
			if (aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1)))
			{
				tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
			}
		}
			 
			tx.success();
			} catch(Exception e){
				
			}
			unmarkAllNodes();
		}
	
	public void increaseGOCAndBitScore2(int minCommonAnnotations){
	System.out.println("increaseGOCAndBitScore");	
	StatementResult result;
	int countRows = 0;
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() ) {
			markUnalignedNodes();
			ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
			ArrayList<Node> record = new ArrayList<Node>();

			result = tx.run("match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)<-[t:SIMILARITY]-(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[s:SIMILARITY]->(o) where (p.marked=true and n.marked=true and m.marked=true and o.marked=true) return o,m,p,n order by length(FILTER(x in p.annotations WHERE x in n.annotations))+length(FILTER(x in o.annotations WHERE x in m.annotations)) desc");
	// baya yawa� result = tx.run("match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1) where (p.marked=true and n.marked=true and m.marked=true and o.marked=true) and length(FILTER(x in p.annotations WHERE x in n.annotations))+length(FILTER(x in o.annotations WHERE x in m.annotations)) >="+minCommonAnnotations+" return o,m,p,n order by length(FILTER(x in p.annotations WHERE x in n.annotations))+length(FILTER(x in o.annotations WHERE x in m.annotations)) desc limit 500");
	// optimizasyon denemesi result = tx.run("match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)<-[:SIMILARITY]-(n:Organism1)-[r:INTERACTS_1]-(m:Organism1) where (p.marked=true and n.marked=true and m.marked=true and o.marked=true) and length(p.annotations) >=5 and length(o.annotations) >=5 and length(m.annotations) >=5 and length(n.annotations) >=5 return o,m,p,n order by length(FILTER(x in p.annotations WHERE x in n.annotations)) desc, length(FILTER(x in o.annotations WHERE x in m.annotations)) desc limit 500");
	//System.out.println("Number of records in query: "+result.list().size());
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
			System.out.println("Number of rows in query:"+countRows);
			Set<Node> aligned = new HashSet<Node>();

	for (int i = 0; i<records.size();i++){
		if (!aligned.contains(records.get(i).get(0))&&!aligned.contains(records.get(i).get(1))&&!aligned.contains(records.get(i).get(2))&&!aligned.contains(records.get(i).get(3)))
		{
			tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
			tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(2).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(3).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
			aligned.add(records.get(i).get(0));aligned.add(records.get(i).get(1));aligned.add(records.get(i).get(2));aligned.add(records.get(i).get(3));
		}
	}
	tx.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked = false )");
	tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = true");
	tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = true");
	result = tx.run("match (p:Organism2)<-[t:SIMILARITY]-(n:Organism1) where (p.marked=true and n.marked=true) and length(FILTER(x in p.annotations WHERE x in n.annotations)) >="+minCommonAnnotations+" return p,n order by length(FILTER(x in p.annotations WHERE x in n.annotations)) desc");
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
		records.add(new ArrayList<Node>(record));
		countRows++;
		}
	System.out.println("Number of rows in query:"+countRows);
	for (int i = 0; i<records.size(); i++){
		if (!aligned.contains(records.get(i).get(0))&&!aligned.contains(records.get(i).get(1)))
		{
			tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
			aligned.add(records.get(i).get(0));aligned.add(records.get(i).get(1));
		}
	}

	tx.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked = false )");
	tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = true");
	tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = true");
	result = tx.run("match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[t:ALIGNS]->(n:Organism1)-[r:INTERACTS_1]-(m:Organism1) where (m.marked=true and o.marked=true) and length(FILTER(x in o.annotations WHERE x in m.annotations)) >="+minCommonAnnotations+" return o,m order by length(FILTER(x in o.annotations WHERE x in m.annotations)) desc");
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
		records.add(new ArrayList<Node>(record));
		countRows++;
		}
	System.out.println("Number of rows in query:"+countRows);
	for (int i = 0; i<records.size(); i++){
		if (!aligned.contains(records.get(i).get(0))&&!aligned.contains(records.get(i).get(1)))
		{
			tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
			aligned.add(records.get(i).get(0));aligned.add(records.get(i).get(1));
		}
	}
		 
		tx.success();
		} catch(Exception e){
			
		}
		unmarkAllNodes();
	}
	
	
	/*
	 * Bu metodun hizalaman�n ba�lar�nda �al��mas� daha uygundur.
	 * 
	 * */
	public void increaseECFirst(int k1, int k2, double sim1, double sim2){
		System.out.println("increaseECFirst");
		StatementResult result;
		int countRows = 0;
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() ){
			markUnalignedNodes();
			ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
			ArrayList<Node> record = new ArrayList<Node>();
			result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)<-[t:SIMILARITY]-(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[s:SIMILARITY]->(o) "
					+ "where length(FILTER(x in p.annotations WHERE x in n.annotations)) >= "+k1+" and length(FILTER(x in o.annotations WHERE x in m.annotations)) >= "+k2+" and t.similarity >= "+sim1+" and s.similarity >= "+sim2+" "
					+"and p.marked = true and n.marked = true and o.marked = true and m.marked = true return o,m,p,n order by t.similarity+s.similarity desc");
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
			System.out.println("Number of rows in query:"+countRows);
			Set<Node> aligned = new HashSet<Node>();
			int x =  0;
			for (int i = 0; i<records.size();i++){
				if (aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1))&&aligned.add(records.get(i).get(2))&&aligned.add(records.get(i).get(3)))
				{
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					x++;
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(2).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(3).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					x++;
				}
			}
			System.out.println(x);
			tx.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked = false )");

			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = true");
			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = true");
	//3 l� klik	
			result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2)-[u3:INTERACTS_2]-(o),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1)-[r3:INTERACTS_1]-(n) where o.marked=true and p.marked=true and q.marked=true and n.marked=true and m.marked=true and l.marked=true return o,p,q,n,m,l limit 1000");
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
						case "p":
							record.add(1,row.get( column.getKey() ).asNode());
							break;
						case "q":
							record.add(2,row.get( column.getKey() ).asNode());
							break;
						case "n":
							record.add(3,row.get( column.getKey() ).asNode());
							break;
						case "m":
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
			System.out.println("Number of rows in query:"+countRows);
			for (int i = 0; i<records.size();i++){
				if (aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1))&&aligned.add(records.get(i).get(2))&&aligned.add(records.get(i).get(3))&&aligned.add(records.get(i).get(4))&&aligned.add(records.get(i).get(5)))
				{
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(3).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(4).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(2).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(5).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
				}
			}
			tx.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked = false )");

			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = true");
			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = true");
	//3 l� klik	
			result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2)-[u3:INTERACTS_2]-(o),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1)-[r3:INTERACTS_1]-(n) where o.marked=true and p.marked=true and q.marked=false and n.marked=true and m.marked=true and l.marked=false return o,p,n,m limit 1000");
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
						case "p":
							record.add(1,row.get( column.getKey() ).asNode());
							break;
						case "n":
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
			System.out.println("Number of rows in query:"+countRows);
			for (int i = 0; i<records.size();i++){
				if (aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1))&&aligned.add(records.get(i).get(2))&&aligned.add(records.get(i).get(3)))
				{
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(2).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(2).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(3).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(1).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
				}
			}

			
			tx.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked = false )");

			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = true");
			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = true");
	//3 l� klik	
			result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2)-[u3:INTERACTS_2]-(o),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1)-[r3:INTERACTS_1]-(n) where o.marked=true and p.marked=false and q.marked=false and n.marked=true and m.marked=false and l.marked=false return o,n limit 1000");
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
			System.out.println("Number of rows in query:"+countRows);
			for (int i = 0; i<records.size();i++){
				if (aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1)))
				{
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
				}
			}
			// 4 l� klik sorgusu
			//tx.run("match (a:Organism1)-[r1:INTERACTS_1]-(b:Organism1)-[r2:INTERACTS_1]-(c:Organism1)-[r3:INTERACTS_1]-(a:Organism1),(c:Organism1)-[r4:INTERACTS_1]-(d:Organism1)-[r5:INTERACTS_1]-(a:Organism1),(d:Organism1)-[r6:INTERACTS_1]-(b:Organism1) return a,b,c,d limit 50");
			
			tx.success();
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		unmarkAllNodes();
		 
	}

	public void increaseECFirst2(int k1, int k2, double sim1, double sim2){
		System.out.println("increaseECFirst");
		StatementResult result;
		int countRows = 0;
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction() ){
			markUnalignedNodes();
			ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
			ArrayList<Node> record = new ArrayList<Node>();
			result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)<-[t:SIMILARITY]-(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[s:SIMILARITY]->(o) "
					+ "where length(FILTER(x in p.annotations WHERE x in n.annotations)) >= "+k1+" and length(FILTER(x in o.annotations WHERE x in m.annotations)) >= "+k2+" and t.similarity >= "+sim1+" and s.similarity >= "+sim2+" "
					+"and p.marked = true and n.marked = true and o.marked = true and m.marked = true return o,m,p,n order by t.similarity+s.similarity desc");
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
			System.out.println("Number of rows in query:"+countRows);
			Set<Node> aligned = new HashSet<Node>();
			for (int i = 0; i<records.size();i++){
				//if (!aligned.contains(records.get(i).get(0))&&!aligned.contains(records.get(i).get(1))&&!aligned.contains(records.get(i).get(2))&&!aligned.contains(records.get(i).get(3)))
				if (!contains(aligned,records.get(i).get(0))&&!contains(aligned,records.get(i).get(1))&&!contains(aligned,records.get(i).get(2))&&!contains(aligned,records.get(i).get(3)))
				{
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(2).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(3).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					aligned.add(records.get(i).get(0)); aligned.add(records.get(i).get(1)); aligned.add(records.get(i).get(2)); aligned.add(records.get(i).get(3));
				}
			}
			tx.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked = false )");

			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = true");
			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = true");
	//3 l� klik	
			result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2)-[u3:INTERACTS_2]-(o),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1)-[r3:INTERACTS_1]-(n) where o.marked=true and p.marked=true and q.marked=true and n.marked=true and m.marked=true and l.marked=true return o,p,q,n,m,l limit 500");
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
						case "p":
							record.add(1,row.get( column.getKey() ).asNode());
							break;
						case "q":
							record.add(2,row.get( column.getKey() ).asNode());
							break;
						case "n":
							record.add(3,row.get( column.getKey() ).asNode());
							break;
						case "m":
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
			System.out.println("Number of rows in query:"+countRows);
			for (int i = 0; i<records.size();i++){
				if (!contains(aligned,records.get(i).get(0))&&!contains(aligned,records.get(i).get(1))&&!contains(aligned,records.get(i).get(2))&&!contains(aligned,records.get(i).get(3))&&!contains(aligned,records.get(i).get(4))&&!contains(aligned,records.get(i).get(5)))
				{
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(3).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(4).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(2).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(5).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					aligned.add(records.get(i).get(0)); aligned.add(records.get(i).get(1)); aligned.add(records.get(i).get(2)); aligned.add(records.get(i).get(3)); aligned.add(records.get(i).get(4)); aligned.add(records.get(i).get(5));
					
				}
			}
			tx.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked = false )");

			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = true");
			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = true");
	//3 l� klik	
			result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2)-[u3:INTERACTS_2]-(o),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1)-[r3:INTERACTS_1]-(n) where o.marked=true and p.marked=true and q.marked=false and n.marked=true and m.marked=true and l.marked=false return o,p,n,m limit 500");
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
						case "p":
							record.add(1,row.get( column.getKey() ).asNode());
							break;
						case "n":
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
			System.out.println("Number of rows in query:"+countRows);
			for (int i = 0; i<records.size();i++){
				if (!contains(aligned,records.get(i).get(0))&&!contains(aligned,records.get(i).get(1))&&!contains(aligned,records.get(i).get(2))&&!contains(aligned,records.get(i).get(3)))
				{
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(2).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(2).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(3).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(1).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					aligned.add(records.get(i).get(0)); aligned.add(records.get(i).get(1)); aligned.add(records.get(i).get(2)); aligned.add(records.get(i).get(3));
				}
			}

			tx.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked = false )");

			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = true");
			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = true");
	//3 l� klik	
			result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2)-[u3:INTERACTS_2]-(o),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1)-[r3:INTERACTS_1]-(n) where o.marked=true and p.marked=false and q.marked=false and n.marked=true and m.marked=false and l.marked=false return o,n limit 500");
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
			System.out.println("Number of rows in query:"+countRows);
			for (int i = 0; i<records.size();i++){
				if (!contains(aligned,records.get(i).get(0))&&!contains(aligned,records.get(i).get(1)))
				{
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					
					aligned.add(records.get(i).get(0));
					aligned.add(records.get(i).get(1));
				}
			}
			// 4 l� klik sorgusu
			//tx.run("match (a:Organism1)-[r1:INTERACTS_1]-(b:Organism1)-[r2:INTERACTS_1]-(c:Organism1)-[r3:INTERACTS_1]-(a:Organism1),(c:Organism1)-[r4:INTERACTS_1]-(d:Organism1)-[r5:INTERACTS_1]-(a:Organism1),(d:Organism1)-[r6:INTERACTS_1]-(b:Organism1) return a,b,c,d limit 50");
			//tx.run("match (e:Organism2)-[r7:INTERACTS_2]-(f:Organism2)-[r8:INTERACTS_2]-(g:Organism2)-[r9:INTERACTS_2]-(e:Organism2),(g:Organism2)-[r10:INTERACTS_2]-(h:Organism2)-[r11:INTERACTS_2]-(e:Organism2),(h:Organism2)-[r12:INTERACTS_2]-(f:Organism2) return e,f,g,h limit 50");
			//tx.run("match (a:Organism1)-[r1:INTERACTS_1]-(b:Organism1)-[r2:INTERACTS_1]-(c:Organism1)-[r3:INTERACTS_1]-(a:Organism1),(c:Organism1)-[r4:INTERACTS_1]-(d:Organism1)-[r5:INTERACTS_1]-(a:Organism1),(d:Organism1)-[r6:INTERACTS_1]-(b:Organism1) match (e:Organism2)-[r7:INTERACTS_2]-(f:Organism2)-[r8:INTERACTS_2]-(g:Organism2)-[r9:INTERACTS_2]-(e:Organism2),(g:Organism2)-[r10:INTERACTS_2]-(h:Organism2)-[r11:INTERACTS_2]-(e:Organism2),(h:Organism2)-[r12:INTERACTS_2]-(f:Organism2) return a,b,c,d,e,f,g,h limit 1000");
			
			tx.success();
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		unmarkAllNodes(); 
	}

	// �lk sorgudan sonraki sorgular ECFirst2 den al�nd�.

	public void increaseConnectedEdges(int minCommonAnnotations){
		System.out.println("increaseConnectedEdges");
		StatementResult result;
		int countRows = 0;
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
	    {
			markUnalignedNodes();
			ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
			ArrayList<Node> record = new ArrayList<Node>();
//			result = tx.run("match (o:Organism2)-[i2:INTERACTS_2]-(n:Organism2)-[r:ALIGNS]->(m:Organism1)-[i1:INTERACTS_1]-(l:Organism1) where n.marked = false and m.marked = false and o.marked = true and l.marked = true "
//			+ "and not (o)-[:ALIGNS]->(l) return o,l");
			//+ "create (o)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: toString(o.proteinName)+'*'+'"+alignmentNo+"'+'*'+toString(l.proteinName), markedQuery: []}]->(l) set o.marked = true, l.marked = true");
			//on create set o.marked = true, l.marked = true
			//HIZLI match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1) return o,m,p,n limit 500
			result = tx.run("match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2)-[u3:INTERACTS_2]-(o) match (n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1)-[r3:INTERACTS_1]-(n) where o.marked=true and p.marked=true and q.marked=true and n.marked=true and m.marked=true and l.marked=true return distinct o,p,q,n,m,l limit 5000");
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
						case "p":
							record.add(1,row.get( column.getKey() ).asNode());
							break;
						case "q":
							record.add(2,row.get( column.getKey() ).asNode());
							break;
						case "n":
							record.add(3,row.get( column.getKey() ).asNode());
							break;
						case "m":
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
			System.out.println("Number of rows in query:"+countRows);
			Set<Node> aligned = new HashSet<Node>();
			System.out.println("Number of rows in query:"+countRows);
			for (int i = 0; i<records.size();i++){
				//if (aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1))&&aligned.add(records.get(i).get(2))&&aligned.add(records.get(i).get(3))&&aligned.add(records.get(i).get(4))&&aligned.add(records.get(i).get(5)))
				if(aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1))&&aligned.add(records.get(i).get(2))&&aligned.add(records.get(i).get(3))&&aligned.add(records.get(i).get(4))&&aligned.add(records.get(i).get(5)))
				//if (!contains(aligned,records.get(i).get(0))&&!contains(aligned,records.get(i).get(1))&&!contains(aligned,records.get(i).get(2))&&!contains(aligned,records.get(i).get(3))&&!contains(aligned,records.get(i).get(4))&&!contains(aligned,records.get(i).get(5)))
				{
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(3).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(4).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(2).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(5).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
				} 
			}
			
			tx.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked = false )");

			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = true");
			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = true");
	//3 l� klik	
			result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2)-[u3:INTERACTS_2]-(o),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1)-[r3:INTERACTS_1]-(n) where o.marked=true and p.marked=true and q.marked=true and n.marked=true and m.marked=true and l.marked=true return o,p,q,n,m,l limit 500");
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
						case "p":
							record.add(1,row.get( column.getKey() ).asNode());
							break;
						case "q":
							record.add(2,row.get( column.getKey() ).asNode());
							break;
						case "n":
							record.add(3,row.get( column.getKey() ).asNode());
							break;
						case "m":
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
			System.out.println("Number of rows in query:"+countRows);
			for (int i = 0; i<records.size();i++){
				if (aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1))&&aligned.add(records.get(i).get(2))&&aligned.add(records.get(i).get(3))&&aligned.add(records.get(i).get(4))&&aligned.add(records.get(i).get(5)))
				{
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(3).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(4).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(2).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(5).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
				}
			}
			tx.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked = false )");

			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = true");
			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = true");
	//3 l� klik	
			result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2)-[u3:INTERACTS_2]-(o),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1)-[r3:INTERACTS_1]-(n) where o.marked=true and p.marked=true and q.marked=false and n.marked=true and m.marked=true and l.marked=false return o,p,n,m limit 500");
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
						case "p":
							record.add(1,row.get( column.getKey() ).asNode());
							break;
						case "n":
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
			System.out.println("Number of rows in query:"+countRows);
			for (int i = 0; i<records.size();i++){
				if (aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1))&&aligned.add(records.get(i).get(2))&&aligned.add(records.get(i).get(3)))
				{
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(2).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(2).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(3).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(1).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
				}
			}

			
			tx.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked = false )");

			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = true");
			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = true");
	//3 l� klik	
			result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2)-[u3:INTERACTS_2]-(o),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1)-[r3:INTERACTS_1]-(n) where o.marked=true and p.marked=false and q.marked=false and n.marked=true and m.marked=false and l.marked=false return o,n limit 500");
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
			System.out.println("Number of rows in query:"+countRows);
			for (int i = 0; i<records.size();i++){
				if (aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1)))
				{
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
				}
			}
			// 4 l� klik sorgusu
			//tx.run("match (a:Organism1)-[r1:INTERACTS_1]-(b:Organism1)-[r2:INTERACTS_1]-(c:Organism1)-[r3:INTERACTS_1]-(a:Organism1),(c:Organism1)-[r4:INTERACTS_1]-(d:Organism1)-[r5:INTERACTS_1]-(a:Organism1),(d:Organism1)-[r6:INTERACTS_1]-(b:Organism1) return a,b,c,d limit 50");
			//tx.run("match (e:Organism2)-[r7:INTERACTS_2]-(f:Organism2)-[r8:INTERACTS_2]-(g:Organism2)-[r9:INTERACTS_2]-(e:Organism2),(g:Organism2)-[r10:INTERACTS_2]-(h:Organism2)-[r11:INTERACTS_2]-(e:Organism2),(h:Organism2)-[r12:INTERACTS_2]-(f:Organism2) return e,f,g,h limit 50");
			//tx.run("match (a:Organism1)-[r1:INTERACTS_1]-(b:Organism1)-[r2:INTERACTS_1]-(c:Organism1)-[r3:INTERACTS_1]-(a:Organism1),(c:Organism1)-[r4:INTERACTS_1]-(d:Organism1)-[r5:INTERACTS_1]-(a:Organism1),(d:Organism1)-[r6:INTERACTS_1]-(b:Organism1) match (e:Organism2)-[r7:INTERACTS_2]-(f:Organism2)-[r8:INTERACTS_2]-(g:Organism2)-[r9:INTERACTS_2]-(e:Organism2),(g:Organism2)-[r10:INTERACTS_2]-(h:Organism2)-[r11:INTERACTS_2]-(e:Organism2),(h:Organism2)-[r12:INTERACTS_2]-(f:Organism2) return a,b,c,d,e,f,g,h limit 1000");
			
			tx.success();
	    } catch (Exception e){
	  	  e.printStackTrace();
	    }
		unmarkAllNodes(); 
	}



	public void increaseConnectedEdges2(int minCommonAnnotations){
		System.out.println("increaseConnectedEdges");
		StatementResult result;
		int countRows = 0;
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
	    {
			markUnalignedNodes();
			ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
			ArrayList<Node> record = new ArrayList<Node>();
//			result = tx.run("match (o:Organism2)-[i2:INTERACTS_2]-(n:Organism2)-[r:ALIGNS]->(m:Organism1)-[i1:INTERACTS_1]-(l:Organism1) where n.marked = false and m.marked = false and o.marked = true and l.marked = true "
//			+ "and not (o)-[:ALIGNS]->(l) return o,l");
			//+ "create (o)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: toString(o.proteinName)+'*'+'"+alignmentNo+"'+'*'+toString(l.proteinName), markedQuery: []}]->(l) set o.marked = true, l.marked = true");
			//on create set o.marked = true, l.marked = true
			//HIZLI match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1) return o,m,p,n limit 500
			result = tx.run("match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2)-[u3:INTERACTS_2]-(o) match (n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1)-[r3:INTERACTS_1]-(n) where o.marked=true and p.marked=true and q.marked=true and n.marked=true and m.marked=true and l.marked=true return distinct o,p,q,n,m,l limit 5000");
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
						case "p":
							record.add(1,row.get( column.getKey() ).asNode());
							break;
						case "q":
							record.add(2,row.get( column.getKey() ).asNode());
							break;
						case "n":
							record.add(3,row.get( column.getKey() ).asNode());
							break;
						case "m":
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
			System.out.println("Number of rows in query:"+countRows);
			Set<Node> aligned = new HashSet<Node>();
			System.out.println("Number of rows in query:"+countRows);
			for (int i = 0; i<records.size();i++){
				//if (aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1))&&aligned.add(records.get(i).get(2))&&aligned.add(records.get(i).get(3))&&aligned.add(records.get(i).get(4))&&aligned.add(records.get(i).get(5)))
				if(noIntersection(aligned,records.get(i)))
				//if (!contains(aligned,records.get(i).get(0))&&!contains(aligned,records.get(i).get(1))&&!contains(aligned,records.get(i).get(2))&&!contains(aligned,records.get(i).get(3))&&!contains(aligned,records.get(i).get(4))&&!contains(aligned,records.get(i).get(5)))
				{
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(3).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(4).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(2).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(5).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					aligned.addAll(records.get(i));
				} 
			}
			
			tx.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked = false )");

			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = true");
			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = true");
	//3 l� klik	
			result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2)-[u3:INTERACTS_2]-(o),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1)-[r3:INTERACTS_1]-(n) where o.marked=true and p.marked=true and q.marked=true and n.marked=true and m.marked=true and l.marked=true return o,p,q,n,m,l limit 500");
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
						case "p":
							record.add(1,row.get( column.getKey() ).asNode());
							break;
						case "q":
							record.add(2,row.get( column.getKey() ).asNode());
							break;
						case "n":
							record.add(3,row.get( column.getKey() ).asNode());
							break;
						case "m":
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
			System.out.println("Number of rows in query:"+countRows);
			for (int i = 0; i<records.size();i++){
				if (!contains(aligned,records.get(i).get(0))&&!contains(aligned,records.get(i).get(1))&&!contains(aligned,records.get(i).get(2))&&!contains(aligned,records.get(i).get(3))&&!contains(aligned,records.get(i).get(4))&&!contains(aligned,records.get(i).get(5)))
				{
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(3).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(4).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(2).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(5).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(2).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					
					aligned.add(records.get(i).get(0));
					aligned.add(records.get(i).get(1));
					aligned.add(records.get(i).get(2));
					aligned.add(records.get(i).get(3));
					aligned.add(records.get(i).get(4));
					aligned.add(records.get(i).get(5));
					
				}
			}
			tx.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked = false )");

			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = true");
			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = true");
	//3 l� klik	
			result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2)-[u3:INTERACTS_2]-(o),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1)-[r3:INTERACTS_1]-(n) where o.marked=true and p.marked=true and q.marked=false and n.marked=true and m.marked=true and l.marked=false return o,p,n,m limit 500");
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
						case "p":
							record.add(1,row.get( column.getKey() ).asNode());
							break;
						case "n":
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
			System.out.println("Number of rows in query:"+countRows);
			for (int i = 0; i<records.size();i++){
				if (!contains(aligned,records.get(i).get(0))&&!contains(aligned,records.get(i).get(1))&&!contains(aligned,records.get(i).get(2))&&!contains(aligned,records.get(i).get(3)))
				{
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(2).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(2).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(3).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(1).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(3).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					
					aligned.add(records.get(i).get(0));
					aligned.add(records.get(i).get(1));
					aligned.add(records.get(i).get(2));
					aligned.add(records.get(i).get(3));
					
				}
			}

			
			tx.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked = false )");

			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = true");
			tx.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = true");
	//3 l� klik	
			result = tx.run( "match (o:Organism2)-[u:INTERACTS_2]-(p:Organism2)-[u2:INTERACTS_2]-(q:Organism2)-[u3:INTERACTS_2]-(o),(n:Organism1)-[r:INTERACTS_1]-(m:Organism1)-[r2:INTERACTS_1]-(l:Organism1)-[r3:INTERACTS_1]-(n) where o.marked=true and p.marked=false and q.marked=false and n.marked=true and m.marked=false and l.marked=false return o,n limit 500");
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
			System.out.println("Number of rows in query:"+countRows);
			for (int i = 0; i<records.size();i++){
				if (!contains(aligned,records.get(i).get(0))&&!contains(aligned,records.get(i).get(1)))
				{
					tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m)");
					
					aligned.add(records.get(i).get(0));
					aligned.add(records.get(i).get(1));
				}
			}
			// 4 l� klik sorgusu
			//tx.run("match (a:Organism1)-[r1:INTERACTS_1]-(b:Organism1)-[r2:INTERACTS_1]-(c:Organism1)-[r3:INTERACTS_1]-(a:Organism1),(c:Organism1)-[r4:INTERACTS_1]-(d:Organism1)-[r5:INTERACTS_1]-(a:Organism1),(d:Organism1)-[r6:INTERACTS_1]-(b:Organism1) return a,b,c,d limit 50");
			//tx.run("match (e:Organism2)-[r7:INTERACTS_2]-(f:Organism2)-[r8:INTERACTS_2]-(g:Organism2)-[r9:INTERACTS_2]-(e:Organism2),(g:Organism2)-[r10:INTERACTS_2]-(h:Organism2)-[r11:INTERACTS_2]-(e:Organism2),(h:Organism2)-[r12:INTERACTS_2]-(f:Organism2) return e,f,g,h limit 50");
			//tx.run("match (a:Organism1)-[r1:INTERACTS_1]-(b:Organism1)-[r2:INTERACTS_1]-(c:Organism1)-[r3:INTERACTS_1]-(a:Organism1),(c:Organism1)-[r4:INTERACTS_1]-(d:Organism1)-[r5:INTERACTS_1]-(a:Organism1),(d:Organism1)-[r6:INTERACTS_1]-(b:Organism1) match (e:Organism2)-[r7:INTERACTS_2]-(f:Organism2)-[r8:INTERACTS_2]-(g:Organism2)-[r9:INTERACTS_2]-(e:Organism2),(g:Organism2)-[r10:INTERACTS_2]-(h:Organism2)-[r11:INTERACTS_2]-(e:Organism2),(h:Organism2)-[r12:INTERACTS_2]-(f:Organism2) return a,b,c,d,e,f,g,h limit 1000");
			
			tx.success();
	    } catch (Exception e){
	  	  e.printStackTrace();
	    }
		unmarkAllNodes(); 
	}


	
	
	/*
	 * Eski Yorum: increaseGOCScore ile hizalanan d���mlerin kom�usu olan d���mlerin de birbiriyle hizalanmas� ile EC art�r�l�r. Kom�u d���mler i�inde �ncelikle GOC ve BitScoreSimilarity art�ranlar tercih edilir.
	 * Yeni Yorum: Hizal� d���mlerin kom�usu olan d���mlerin de birbiriyle hizalanmas� ile EC art�r�l�r.
	 * 
	 * 
	 * T�m D��er Metotlarla ilgili: �u y�ntem yanl�� if (aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1))&&aligned.add(records.get(i).get(2))&&aligned.add(records.get(i).get(3)))
	 * 
	 *  Bu metot kenar i�ermeyen e�le�melerin say�s�n�n �ok olmas� durumunda daha �ok i�e yarayacakt�r.
	 * */

	public void increaseECByAddingPair(){
		
		StatementResult result;
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
	    {
			markUnalignedNodes();
			ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
			ArrayList<Node> record = new ArrayList<Node>();
			result = tx.run("match (o:Organism2)-[i2:INTERACTS_2]-(n:Organism2)-[r:ALIGNS]->(m:Organism1)-[i1:INTERACTS_1]-(l:Organism1) where n.marked = false and m.marked = false and o.marked = true and l.marked = true "
			+ "and not (o)-[:ALIGNS]->(l) return o,l");
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
			
			for (int i = 0; i<records.size();i++){
				if (aligned.add(records.get(i).get(0))&&aligned.add(records.get(i).get(1)))
				tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m) set n.marked = true and m.marked = true");
			}
			 
			tx.success();
	    } catch (Exception e){
	  	  e.printStackTrace();
	    }
		unmarkAllNodes();
		
		
	}

	public void increaseECByAddingPair2(){
		
		StatementResult result;
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
	    {
			markUnalignedNodes();
			ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
			ArrayList<Node> record = new ArrayList<Node>();
			result = tx.run("match (o:Organism2)-[i2:INTERACTS_2]-(n:Organism2)-[r:ALIGNS]->(m:Organism1)-[i1:INTERACTS_1]-(l:Organism1) where n.marked = false and m.marked = false and o.marked = true and l.marked = true "
			+ "and not (o)-[:ALIGNS]->(l) return o,l");
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
			
			for (int i = 0; i<records.size();i++){
				if (noIntersection(aligned,records.get(i)))
				tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m) set n.marked = true and m.marked = true");
				aligned.addAll(records.get(i));
			}
			
			tx.success();
	    } catch (Exception e){
	  	  e.printStackTrace();
	    }
		unmarkAllNodes(); 
	}

	// increaseECByAddingPair2 ile aynı sonuçları üretiyor.

	public void increaseECByAddingPair3(){
		
		StatementResult result;
		
		try ( org.neo4j.driver.v1.Transaction tx = session.beginTransaction())
	    {
			markUnalignedNodes();
			ArrayList<ArrayList<Node>> records = new ArrayList<ArrayList<Node>>();
			ArrayList<Node> record = new ArrayList<Node>();
			result = tx.run("match (o:Organism2)-[i2:INTERACTS_2]-(n:Organism2)-[r:ALIGNS]->(m:Organism1)-[i1:INTERACTS_1]-(l:Organism1) where n.marked = false and m.marked = false and o.marked = true and l.marked = true "
			+ "and not (o)-[:ALIGNS]->(l) return o,l");
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
			
			for (int i = 0; i<records.size();i++){
				if (!aligned.contains(records.get(i).get(0))&&!aligned.contains(records.get(i).get(1)))
				tx.run("match (n:Organism2 {proteinName: '"+records.get(i).get(0).get("proteinName").asString()+"'}), (m:Organism1 {proteinName: '"+records.get(i).get(1).get("proteinName").asString()+"'}) create (n)-[:ALIGNS {alignmentNumber: '"+alignmentNo+"', alignmentIndex: '"+records.get(i).get(0).get("proteinName").asString()+"*"+alignmentNo+"*"+records.get(i).get(1).get("proteinName").asString()+"',markedQuery:[]}]->(m) set n.marked = true and m.marked = true");
				aligned.add(records.get(i).get(0)); aligned.add(records.get(i).get(1));
			}
			
			tx.success();
	    } catch (Exception e){
	  	  e.printStackTrace();
	    }
		unmarkAllNodes(); 
	}
	
	private void markUnalignedNodes(){
		try(org.neo4j.driver.v1.Transaction t = innerSession.beginTransaction()){
			t.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism2) where not (x in deneme) set x.marked = true");
			t.run("match (n:Organism2)-[rr:ALIGNS]->(m:Organism1) with collect(n) as deneme, collect(m) as dene match (x:Organism1) where not (x in dene) set x.marked = true");
			t.success();
		} catch(Exception e){
			e.printStackTrace();
		}
	}

	private void unmarkAllNodes(){
		try(org.neo4j.driver.v1.Transaction t = innerSession.beginTransaction()){
			t.run("match p=(n) FOREACH (x IN nodes(p) | SET x.marked = false )");
			t.success();
		} catch(Exception e){
			e.printStackTrace();
		}
	}
	
	private boolean contains(Set<Node> s, Node n){
		for (Node node : s) {
			if(node.equals(n))	
			return true;
		}
		return false;
	}
	
	private boolean noIntersection(Set<Node> aligned, ArrayList<Node> record){
		for (Node node : record) {
			if(aligned.contains(node))
				return false;
			else aligned.add(node);
		}
		return true;
	}

}
