

import java.io.BufferedReader;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.FileReader;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.*;
        
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.conf.*;
import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
        
public class KMeans {
	
	//function to convert a data file in to an array list of doubles (ignore cost line)
	public static ArrayList<ArrayList<Double>> FileToListOfLists(String FileName){
		BufferedReader br = null;
		ArrayList<ArrayList<Double>> ll = null;
		try { 
			String sCurrentLine;
			br = new BufferedReader(new FileReader(FileName));
			ll = new ArrayList<ArrayList<Double>>(); 
			while ((sCurrentLine = br.readLine()) != null) {
				//System.out.println("This line is: " + sCurrentLine);
        StringTokenizer tokenizer = new StringTokenizer(sCurrentLine);
    		ArrayList<Double> a = new ArrayList<Double>();
        while (tokenizer.hasMoreTokens()){
        	String word=tokenizer.nextToken();
        	if (word.equalsIgnoreCase("cost")) {
        		//System.out.println("cost line");
        		break;
        	}
        	else{
          	a.add(Double.parseDouble(word));        		
        	}
        }
        if(a.size()>0){
        ll.add(a);
        }
        //System.out.println(" a is: " + a.toString());
        //System.out.println("dim a is: " + a.size());
			} 
			br.close();
			return ll;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return ll;
	}
	
	
  public static double getDistance(ArrayList<Double> a, ArrayList<Double> b){
  	double Dist=0;
  	for (int i=0; i< a.size();i++){
  		Dist = Dist + Math.pow((a.get(i)-b.get(i)),2);
  	}
  	return Dist;
  }

    public static class Map1 extends Mapper<LongWritable, Text, Text, Text> {
      private Text point = new Text();
      private Text centr = new Text();
      
      public void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
        
        Configuration conf = context.getConfiguration();
        String CentFile=conf.get("centroid_file");
        //System.out.println(CentFile);
        ArrayList<ArrayList<Double>> Centroids = FileToListOfLists(CentFile);
        //System.out.println("Centroids: "  + Centroids.toString());
      	
        // Read string of point coords to an ArrayList	      	
      	String line = value.toString();
        StringTokenizer tokenizer = new StringTokenizer(line);

    		ArrayList<Double> pt = new ArrayList<Double>();
        
    		while (tokenizer.hasMoreTokens()){
        	pt.add(Double.parseDouble(tokenizer.nextToken()));
        }

    		//System.out.println("Point: " + pt);
  
        double mindist=getDistance(pt, Centroids.get(0)); //initialize min dist w/ ref to 1st centroid
        String centAssigned = Centroids.get(0).toString();
    		for (int i=1; i< Centroids.size();i++){
          //System.out.println("mindist: " + mindist);
          Double dist = getDistance(pt,Centroids.get(i));
    			if (dist < mindist){
    				centAssigned = Centroids.get(i).toString();
    				mindist = dist;
    			}
    		}

    		//System.out.println("Centroid: " + centAssigned);
        //System.out.println("mindist: " + mindist);
    		
    		point.set(pt.toString());
    		centr.set(centAssigned);
    		
    		context.write(centr,point);
    		context.write(new Text("cost"), new Text(String.valueOf(mindist)));
      }
    }
 
      public static class Reduce1 extends Reducer<Text, Text, Text, Text> {
        private Text k = new Text();
        private Text v = new Text();

        public void reduce(Text key, Iterable<Text> values, Context context) 
          throws IOException, InterruptedException {
        	System.out.println("reducer for centroid: " + key);

        	if (key.toString().equalsIgnoreCase("cost")){
        		//System.out.println("entering cost loop");
            double costsum=0;
        		for (Text value : values) { 
        			costsum = costsum + Double.parseDouble(value.toString());
        	}
      			context.write(key, new Text(String.valueOf(Math.round(costsum))));
        	}
        	else{
          	//Parse cost and coords of point from each value
          	ArrayList<Double> coords = new ArrayList<Double>();
          	
          	int val_counter=0;
          	
            for (Text value : values) { 
            	String pt = value.toString();         	
            	pt=pt.substring(1, pt.length()-1); // remove brackets from arraylist for points
            	String[] pt_coords=pt.split(",");
            	            	
            	int pt_counter=0;
            	for (String pt_coord : pt_coords){ // convert point coords in to doubles and add for all pts
            		if (val_counter==0){
            			coords.add(pt_counter, Double.parseDouble(pt_coord));
            		}
            		else{
            			coords.set(pt_counter, coords.get(pt_counter) + Double.parseDouble(pt_coord));
            		}
            		pt_counter = pt_counter + 1;
            	}
            	val_counter=val_counter+1;
            }
            
            
            //normalize to get centroid values
            for (int i=0; i<coords.size();i++){
              DecimalFormat decim = new DecimalFormat("0.000");
              Double comp = Double.parseDouble(decim.format(coords.get(i)/val_counter));
            	coords.set(i, comp);
            }
            
            System.out.println("number of values: " + val_counter);
            System.out.println("new coords: " + coords.toString());
            
            String centvals = coords.toString();
            centvals = centvals.substring(1, centvals.length()-1); //remove brackets
            centvals=centvals.replace(", ","\t"); // add tabs for commas before writing to file          
            
          	k.set("");
          	v.set(centvals);
          	context.write(k,v);        
        	}

        }
      }
        
 public static void main(String[] args) throws Exception {
    Configuration conf = new Configuration();
    
    
    String InputFile = ("/home/cs246/Desktop/kmeansdata/data");
    String OutputFile = ("/home/cs246/Desktop/kmeansdata/centroids/");
    String CentFile = ("/home/cs246/Desktop/kmeansdata/c2.txt");
    conf.set("centroid_file", CentFile);
    
    for (int i=0;i<21;i++) {

    	Job job = new Job(conf, "KMeans");
      job.setJarByClass(KMeans.class);
      job.setOutputKeyClass(Text.class);
      job.setOutputValueClass(Text.class);
          
      job.setMapperClass(Map1.class);
      job.setReducerClass(Reduce1.class);
          
      job.setInputFormatClass(TextInputFormat.class);
      job.setOutputFormatClass(TextOutputFormat.class);
      
      FileInputFormat.addInputPath(job, new Path(InputFile));
      FileOutputFormat.setOutputPath(job, new Path(OutputFile+String.valueOf(i+1)));
      job.waitForCompletion(true);      
      
      conf.set("centroid_file", OutputFile+String.valueOf(i+1)+"/part-r-00000");
    }
 }
        
}