package typed.ppi;

public class MetaData {
	
	double noofPercentileSteps = 10.0;
	
	double[] annotatedSimilarity;
	double[] similarity;
	
	double[] organism1Power2;
	double[] organism1Power3;
	double[] organism1Power4;
	double[] organism2Power2;
	double[] organism2Power3;
	double[] organism2Power4;
	
	double[] organism1Pagerank;
	double[] organism1Betweenness ;
	double[] organism1Closeness;
	double[] organism1Harmonic;
	double[] organism2Pagerank;
	double[] organism2Betweenness;
	double[] organism2Closeness;
	double[] organism2Harmonic;
	
	MetaData(){
		noofPercentileSteps = 10.0;
		annotatedSimilarity = new double[10];
		similarity = new double[10];
		organism1Power2 = new double[10];
		organism1Power3 = new double[10];
		organism1Power4 = new double[10];
		organism2Power2 = new double[10];
		organism2Power3 = new double[10];
		organism2Power4 = new double[10];
		
		organism1Pagerank = new double[10];
		organism1Betweenness = new double[10];
		organism1Closeness = new double[10];
		organism1Harmonic= new double[10];
		organism2Pagerank = new double[10];
		organism2Betweenness = new double[10];
		organism2Closeness = new double[10];
		organism2Harmonic= new double[10];
	}
	
	MetaData(int n) {
		noofPercentileSteps = (double)n;
		annotatedSimilarity = new double[n];
		similarity = new double[n];
		organism1Power2 = new double[n];
		organism1Power3 = new double[n];
		organism1Power4 = new double[n];
		organism2Power2 = new double[n];
		organism2Power3 = new double[n];
		organism2Power4 = new double[n];
		
		organism1Pagerank = new double[n];
		organism1Betweenness = new double[n];
		organism1Closeness = new double[n];
		organism1Harmonic= new double[n];
		organism2Pagerank = new double[n];
		organism2Betweenness = new double[n];
		organism2Closeness = new double[n];
		organism2Harmonic= new double[n];
	}

}
