package typed.ppi;


//import java.util.ArrayList;
//import java.util.Set;
//import org.neo4j.driver.v1.types.Node;

import typed.ppi.SubGraph;

import java.util.List;
import java.util.Set;

import akka.actor.ActorRef;
import akka.actor.TypedActor.Receiver;
import scala.concurrent.ExecutionContextExecutor;
import scala.concurrent.Future;

public interface Aligner extends Receiver {
	
	public void createAlignment(String alignment);
	public void createAlignment(SubGraph alignment);
	public void createAlignment(long markedQuery,int minFrequency);
	public void copyAlignment(int alignmentNo);
	public void addAlignment(String alignment);
	public void addAlignment(long markedQuery,int minFrequency);
	public void addAlignment(SubGraph alignment);
	public void addAlignment(int alignmentNo);
	public Future<SubGraph> currentAlignment(ExecutionContextExecutor dispatcher);
	public Future<SubGraph> subGraphWithAlignedEdges(ExecutionContextExecutor executionContextExecutor);
	public Future<Long> markAlignedEdges(long markedQuery, ExecutionContextExecutor dispatcher);
	public Future<SubGraph> subGraphWithDoubleAlignedEdges(ExecutionContextExecutor executionContextExecutor);
	public Future<Long> markDoubleAlignedEdges(long markedQuery, ExecutionContextExecutor dispatcher);
	public Future<SubGraph> subGraphWithStrongInducedEdges(ExecutionContextExecutor executionContextExecutor,int simTreshold, int annotationTreshold,int powerTreshold);
	public Future<Long> markConservedStructures(ExecutionContextExecutor dispatcher,long markedQuery,int simTreshold, int annotationTreshold,int powerTreshold);
	public Future<Long> markConservedStructureQuery(ExecutionContextExecutor executionContextExecutor, long markedQuery);
	public Future<SubGraph> subGraphWithXBitScoreSimilarity(double treshold,ExecutionContextExecutor executionContextExecutor);
	public Future<Long> markXBitScoreSimilarity(double treshold, long markedQuery, ExecutionContextExecutor dispatcher);
	public Future<SubGraph> subGraphWithKGOTerms (int k,ExecutionContextExecutor executionContextExecutor);
	public Future<Long> markKGOTerms (int k, long markedQuery, ExecutionContextExecutor dispatcher);
	public Future<SubGraph> subGraphWithTopAlignedPowerNodes(int limit, String algorithm, ExecutionContextExecutor dispatcher);
	public Future<Long> markTopAlignedPowerNodes(int limit, long markedQuery, String algorithm, ExecutionContextExecutor dispatcher);
	public Future<SubGraph> subGraphWithAlignedPowerNodes(int minCommonAnnotations, int power2, int power3, int power4, ExecutionContextExecutor dispatcher);
	public Future<Long> markAlignedPowerNodes(int minCommonAnnotations, int power2, int power3, int power4, long markedQuery, ExecutionContextExecutor dispatcher);
	public Future<SubGraph> subGraphWithAlternativeCentralNodes(int minCommonAnnotations, double score1, double score2, String algorithm,ExecutionContextExecutor dispatcher);
	public Future<Long> markAlternativeCentralNodes(int minCommonAnnotations, double score1, double score2, String algorithm,long markedQuery, ExecutionContextExecutor dispatcher);
	public Future<SubGraph> subGraphWithClusterEdges(int minCommonAnnotations, String clusterType,long clusterIDOfOrganism1, long clusterIDOfOrganism2,ExecutionContextExecutor dispatcher);
	public Future<Long> markClusterEdges(int minCommonAnnotations, String clusterType,long clusterIDOfOrganism1, long clusterIDOfOrganism2,long markedQuery, ExecutionContextExecutor dispatcher);
	public void increaseBitScoreAndGOC(int minCommonAnnotations, char mode);
	public void increaseGOCAndBitScore(int minCommonAnnotations, char mode);
	public void increaseBitScoreWithTopMappings(int limit, char mode) ;
	public void increaseGOCWithTopMappings(int limit, char mode);
	public void increaseECWithFunctionalParameters(int k1, int k2, double sim1, double sim2, char mode);
	public void increaseFunctionalParametersWithPower(int minCommonAnnotations, double sim,int power, char powerMode, char mode); 
	public void increaseConnectedEdges(int limit, int minCommonAnnotations, boolean doubleOrTriple, char mode);
	public void increaseECByAddingPair(int minCommonAnnotations, double sim, char mode);
	public void increaseCentralEdges(int power2, int power3, int power4, char mode) ;
	public Future<Boolean>  alignCentralPowerNodes(int minCommonAnnotations, double sim, int power2, int power3, int power4, char mode);
	public Future<Boolean> alignAlternativeCentralNodes(int minCommonAnnotations, double sim, double score1, double score2, String algorithm, char mode);
	public void alignClusters(int minCommonAnnotations,double sim, String clusterType, long clusterIDOfOrganism1, long clusterIDOfOrganism2, char mode);
	public void alignClusterEdges(int minCommonAnnotations, String clusterType,long clusterIDOfOrganism1, long clusterIDOfOrganism2,boolean addPair, char mode);
	public void addMeaninglessMapping(int limit, char mode);
	public void removeBadMappings(int k, double sim, boolean keepEdges, int limit);
	public void removeMappingsWithoutEdges(int limit);
	public void removeBadMappingsToReduceInduction1(int k, double sim, boolean keepEdges,int simTreshold, int annotationTreshold,int powerTreshold);
	public void removeBadMappingsToReduceInduction2(int k, double sim, boolean keepEdges,int simTreshold, int annotationTreshold,int powerTreshold);
	public void reduceInduction1(int simTreshold, int annotationTreshold,int powerTreshold);
	public void reduceInduction2();
	public void removeInductiveMappings1(int simTreshold, int annotationTreshold,int powerTreshold);
	public void removeInductiveMappings2();
	public void removeAlignment();
	public void removeAlignment(String markedQuery);
	public void removeLatterOfManyToManyAlignments();
	public void removeWeakerOfManyToManyAlignments(int simTreshold, int annotationTreshold,int powerTreshold);
	public SubGraph findUnalignedNodes();
	public Future<SubGraph> requestCharacteristicSubAlignments(int k, double x, boolean edges, ExecutionContextExecutor executionContextExecutor);
	public int getAlignmentNo();
	public BenchmarkScores getBenchmarkScores() ;
	public void setBenchmarkScores(BenchmarkScores bs) ;
	public void onReceive(Object message, ActorRef sender);
	public void writeAlignmentToDisk(String alignmentName);
	public void writeAlignmentToDisk(String alignmentName, String markedQuery);
	public String getMesaj() ;
	public void setMesaj(String mesaj) ;
	public List<BenchmarkScores> getMarkedQueries();
	public void setMarkedQueries(List<BenchmarkScores> markedQueries);
	public void addMarkedQueries(Set<BenchmarkScores> markedQueries);
	public int getNoofCyclesAlignmentUnchanged();
	public void setNoofCyclesAlignmentUnchanged(int noofCyclesAlignmentUnchanged);
	
}
