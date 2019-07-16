
package typed.ppi;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class BenchmarkScores {
	
	int alignmentNo;
	long markedQuery;
	String alignmentFile;
	SubGraph alignment;
	
	double EC = 0;
	double ICS = 0;
	double S3 = 0;
	double GOC = 0;
	int GOEnrichment = 0;
	double bitScore = 0;
	int LCCS = 0;

	int size = 1;
	
	public BenchmarkScores(int alignmentNo) {
		this();
		this.alignmentNo = alignmentNo;
	}
	
	public BenchmarkScores(long markedQuery) {
		this();
		this.markedQuery = markedQuery;
	}
	
	public BenchmarkScores(int alignmentNo,long markedQuery) {
		this();
		this.alignmentNo = alignmentNo;
		this.markedQuery = markedQuery;
	}
	
	public BenchmarkScores(int alignmentNo, long markedQuery, BenchmarkScores bs) {
		this();
		this.alignmentNo = alignmentNo;
		this.markedQuery = markedQuery;
		EC = bs.EC;
		ICS = bs.ICS;
		S3 = bs.S3;
		LCCS = bs.LCCS;
		GOC = bs.GOC;
		GOEnrichment = bs.GOEnrichment;
		bitScore = bs.bitScore;
		size = bs.size; 
	}

	public BenchmarkScores() {
		// TODO Auto-generated constructor stub
		super();
		EC = 0;
		ICS = 0;
		S3 = 0;
		LCCS = 0;
		GOC = 0;
		GOEnrichment = 0;
		bitScore = 0;
		size = 1;
	}

	public double getEC() {
		return EC;
	}

	public void setEC(double eC) {
		EC = eC;
	}

	public double getICS() {
		return ICS;
	}

	public void setICS(double iCS) {
		ICS = iCS;
	}

	public double getS3() {
		return S3;
	}

	public void setS3(double s3) {
		S3 = s3;
	}
	
	public int getLCCS() {
		return LCCS;
	}

	public void setLCCS(int lCCS) {
		LCCS = lCCS;
	}

	public double getGOC() {
		return GOC;
	}

	public void setGOC(double gOC) {
		GOC = gOC;
	}
	
	public int getGOEnrichment() {
		return GOEnrichment;
	}

	public void setGOEnrichment(int gOE) {
		GOEnrichment = gOE;
	}

	public double getBitScore() {
		return bitScore;
	}

	public void setBitScore(double bitScore) {
		this.bitScore = bitScore;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}
	
	public int getAlignmentNo() {
		return this.alignmentNo;
	}
	
public boolean isEqualTo(BenchmarkScores bs) {
		
		if(this.getEC()==bs.getEC() && this.getICS()==bs.getICS() && this.getS3()==bs.getS3() &&this.getBitScore()==bs.getBitScore() && this.getGOC()==bs.getGOC() &&this.getSize()==bs.getSize())
			return true;
		return false;
	}
	
	public boolean isParetoDominantThan(BenchmarkScores bs) {
		
		if(this.getEC()>=bs.getEC() && this.getICS()>=bs.getICS() && this.getS3()>=bs.getS3() &&this.getBitScore()>=bs.getBitScore() && this.getGOC()>=bs.getGOC())
			return true;
		return false;
	}
	// true yapılan ölçütlere göre mevcut kıyaslama puanının daha başarılı olma durumunu döndürür.
	public boolean isDominantThan(BenchmarkScores bs,boolean ec, boolean ics, boolean s3, boolean goc, boolean bitscore, boolean size) {
		
		if(ec)
			if(this.getEC()<bs.getEC())
				return false;
		if(ics)
			if(this.getICS()<bs.getICS())
				return false;
		if(s3)
			if(this.getS3()<bs.getS3())
				return false;
		if(goc)
			if(this.getGOC()<bs.getGOC())
				return false;
		if(bitscore)
			if(this.getBitScore()<bs.getBitScore())
				return false;
		if(size)
			if(this.getSize()<bs.getSize())
				return false;
		return true;
	}
	// true yapılan ölçütlere göre mevcut kıyaslama puanının daha başarılı olduğu ölçütlerin sayısını döndürür
	public int countOfBetterBenchmarks(BenchmarkScores bs) {
		short score = 0;
			if(this.getEC()>bs.getEC())
				score++;
			if(this.getICS()>bs.getICS())
				score++;
			if(this.getS3()>bs.getS3())
				score++;
			if(this.getGOC()>bs.getGOC())
				score++;
			if(this.getBitScore()>bs.getBitScore())
				score++;
			if(this.getSize()>bs.getSize())
				score++;
		return score;
	}
	
	public List<String> listOfBetterBenchmarks(BenchmarkScores bs) {
		List<String> ls = new ArrayList<String>();
			if(this.getEC()>bs.getEC())
				ls.add("EC");
			if(this.getICS()>bs.getICS())
				ls.add("ICS");
			if(this.getS3()>bs.getS3())
				ls.add("S3");
			if(this.getGOC()>bs.getGOC())
				ls.add("GOC");
			if(this.getBitScore()>bs.getBitScore())
				ls.add("BS");
		return ls;
	}
	
	public BenchmarkScores difference(BenchmarkScores bs, String resultFile) {
		BenchmarkScores temp = new BenchmarkScores();
		
		temp.EC = this.EC - bs.EC;
		temp.ICS = this.ICS - bs.ICS;
		temp.S3 = this.S3 - bs.S3;
		temp.LCCS = this.LCCS - bs.LCCS;
		temp.GOC = this.GOC - bs.GOC;
		temp.bitScore = this.bitScore - bs.bitScore;
		temp.size = this.size - bs.size;
		
		temp.alignmentNo = this.alignmentNo == bs.alignmentNo  ? this.alignmentNo:  0;
		temp.alignmentFile = this.alignmentFile == bs.alignmentFile  ? this.alignmentFile:  this.differenceFromAlignmentFile(bs.alignmentFile, resultFile);
		temp.alignment = this.alignment == bs.alignment  ? this.alignment:  this.alignment.difference(bs.alignment);
		temp.markedQuery = this.markedQuery == bs.markedQuery  ? this.markedQuery:  0;
		
		return temp;
	}

	@Override
	public String toString() {
		return "BenchmarkScores [alignmentNo=" + alignmentNo + ", markedQuery=" + markedQuery + ", EC=" + EC + ", ICS="
				+ ICS + ", S3=" + S3 + ", LCCS = "+LCCS+", GOC=" + GOC + ", GOEnrichment=" + GOEnrichment + ", bitScore=" + bitScore + ", size=" + size + "]";
	}
	
	public String differenceFromAlignmentFile(String differentiatingAlignmentFile, String resultFile) {
		
		if (resultFile == null || resultFile == "")
			return "";
		
		try {
			
			FileReader fr = new FileReader(differentiatingAlignmentFile);
			BufferedReader br =  new BufferedReader(fr); 
			String line;
			HashSet<String> hs = new HashSet<String>();
			String[] pair;
			while((line = br.readLine())!=null)
			{
				pair = line.split(" ");
				hs.add(pair[0]);hs.add(pair[1]);			
			}
			
			fr = new FileReader(this.alignmentFile);
			br =  new BufferedReader(fr); 
			BufferedWriter bw = new BufferedWriter(new FileWriter(resultFile));
			while((line = br.readLine())!=null)
			{
				pair = line.split(" ");
				if(!hs.contains(pair[0])&&!hs.contains(pair[1])) {
					bw.write(pair[0]+" "+pair[1]);
					bw.newLine();
				}
				
			}
			br.close();
			bw.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return resultFile;
	}

}
