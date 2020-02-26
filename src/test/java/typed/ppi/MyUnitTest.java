package typed.ppi;

import org.junit.Test;
import static org.junit.Assert.*;

public class MyUnitTest {

    @Test
    public void testInitializePreviousAlignmentsFromFolder() {
        AkkaSystem myUnit = new AkkaSystem(1,"neo4j-community-3.5.6","evet",100,20,10,300);
        myUnit.computeMetaData();
        myUnit.removeAllAlignments();
        int result = myUnit.initializePreviousAlignmentsFromFolder(1, "/home/giray/4/dorderdorder", "aln");
        System.out.println(result);
        for (int i = 1;i<result+1;i++) {
        	Aligner a = new AlignerImpl(myUnit,i);
        	myUnit.calculateGlobalBenchmarks(a);
        	a.increaseECByAddingPair(0, 0, '3');
        }
        
        myUnit.writeAlignments("TestSave", "/home/giray/4/dorderdorder","aln");
        myUnit.saveOldMarkedQueriesToFile("test.txt", "/home/giray/4/dorderdorder");
        myUnit.printBenchmarkStatistics(1, "teststatistics", "/home/giray/4/dorderdorder");
//        ArrayList<String> test =new ArrayList<String>( );
//        test.add("deneme");
//        test.add("olabilir");
        assertEquals(10, result);

    }
}
