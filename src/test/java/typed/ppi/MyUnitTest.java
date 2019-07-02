package typed.ppi;

import org.junit.Test;
import static org.junit.Assert.*;

public class MyUnitTest {

    @Test
    public void testInitializePreviousAlignmentsFromFolder() {
        AkkaSystem myUnit = new AkkaSystem(1,"neo4j-community-3.5.6",100,20);

        int result = myUnit.initializePreviousAlignmentsFromFolder(1, "/home/giray/ppi/", "alignment");

        assertEquals(15, result);

    }
}
