
package kikoqiu;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.*;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.Callable;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;


@Command(name = "img2pdf", mixinStandardHelpOptions = true, version = "img2pdf 0.1",
         description = "img2pdf utils")
class img2pdf implements Callable<Integer>{	
	@Parameters(index = "0",description = "The directory including the images.")
	String image_dir;
	@Parameters(index = "1", description = "The result pdf file.")
    String pdf_file;

	//public String param="";
	@Option(names = { "-s", "--split-page" },  description = "split the image into 2 pages")
	public boolean split=false;
	@Option(names = { "-m", "--maximum-height" },  description = "adjust height to maximum")
	public boolean heightm=false;
	@Option(names = { "-i", "--image-output" },  description = "write pages to the dir")
    public boolean imageout=false;
    @Option(names = { "-o", "--sort-order" }, defaultValue="asc", description = "sort the image asc|desc")
    public String sortOrder="asc";
    @Option(names = { "-r", "--reverse" },  description = "transform pdf to images")
    public boolean reverse=false;
    
	//@Option(names = { "-h", "--help" }, usageHelp = true, description = "display a help message")
	//public boolean help=false;
	
	@Override
    public Integer call() throws Exception {
        /*if(this.pdf==null){
            this.pdf=this.dir+".pdf";
        }*/
		System.err.println("dir: " + image_dir);
		System.err.println("pdf: " + pdf_file);
		System.err.println("spilt: " + split);
		System.err.println("heightm: " + heightm);
        System.err.println("imageout: " + imageout);
        System.err.println("reverse: " + reverse);
        if(this.reverse){
            return this.pdf2img();
        }		
		
		java.util.List<String> dsts=new java.util.ArrayList<String> ();
        List<File> files= Arrays.asList(new java.io.File(image_dir).listFiles());
        files=sortFileByName(files,this.sortOrder);
        for(File f:files){
            if(f.exists() && !f.isDirectory()){
                dsts.add(f.getPath());
            }
        }

        jpgToPdf(dsts,pdf_file);
		
        return 0;
    }

    public static void main(String... args) {
        int exitCode = new CommandLine(new img2pdf()).execute(args);
        System.exit(exitCode);
    }
	
    
    
	
	public  List<File> sortFileByName(List<File> files, final String orderStr) {
        if (!orderStr.equalsIgnoreCase("asc") && orderStr.equalsIgnoreCase("desc")) {
            return files;
        }
        File[] files1 = files.toArray(new File[0]);
        Arrays.sort(files1, new Comparator<File>() {
            public int compare(File o1, File o2) {
				String[] o1s=o1.getName().split("[^\\d]+");
				String[] o2s=o2.getName().split("[^\\d]+");
				//System.err.println(o1.getName());
				//System.err.println(o2.getName());
				for(int i=0;i<o1s.length && i<o2s.length;++i){					
					//System.err.println(o1s[i]);
					//System.err.println(o2s[i]);
					if(o1s[i].equals(o2s[i]))continue;
					long n1 = Long.parseLong(o1s[i]);
					long n2 = Long.parseLong(o2s[i]);
					if(n1!=n2)return (int)(orderStr.equalsIgnoreCase("asc")?n1-n2:n2-n1);
				}				
                long n1 = o1s.length;
				long n2 = o2s.length;
				return (int)(orderStr.equalsIgnoreCase("asc")?n1-n2:n2-n1);
            }
        });
		for(File f:files1){
			System.err.println(f.getName());
		}
        return new ArrayList<File>(Arrays.asList(files1));
    }
	

 
	public  PDRectangle calcPageSize(java.util.List<String> files) throws IOException {
        int swidth=99999999,sheight=99999999,mwidth=0,mheight=0;
		float sratio=999.0f;
		float mratio=0.0f;
        for(String file :files){
            BufferedImage image = ImageIO.read(new java.io.File(file));
			if(image==null){
				continue;
			}
            int width=image.getWidth();
			int height=image.getHeight();
			float ratio=width*1.0f/height;
			if(width<swidth){
				swidth=width;
			}
			if(height<sheight){
				sheight=height;
			}
			if(width>mwidth){
				mwidth=width;
			}
			if(height>mheight){
				mheight=height;
			}
			if(ratio<sratio){
				sratio=ratio;
			}
			if(ratio>mratio){
				mratio=ratio;
			}
        }
		
		if(split){
			//if(mratio>=sratio*1.6f){
				System.err.println("page size:"+mwidth/2+","+mheight);
				return new PDRectangle(mwidth/2,mheight);
			//}
		}
		System.err.println("page size:"+mwidth+","+mheight);
		return new PDRectangle(mwidth,mheight);
    }
	public  void addImagePage(PDDocument pdDocument,BufferedImage image)throws IOException{
		PDPage pdPage = new PDPage(new PDRectangle(image.getWidth(), image.getHeight()));
		pdDocument.addPage(pdPage);
		//PDImageXObject pdImageXObject = LosslessFactory.createFromImage(pdDocument, image);
		PDImageXObject pdImageXObject = JPEGFactory.createFromImage(pdDocument, image,0.9f);
		PDPageContentStream contentStream = new PDPageContentStream(pdDocument, pdPage);
		contentStream.drawImage(pdImageXObject, 0, 0, image.getWidth(), image.getHeight());
		contentStream.close();
	}
	
    public  void jpgToPdf(java.util.List<String> files, String pdfPath) throws IOException {

        PDDocument pdDocument = new PDDocument();
        PDRectangle basicSize=calcPageSize(files);
        java.io.File imgoutDir=new java.io.File(pdfPath+"_img");
        int imgout_index=1;
		if(this.imageout){
            imgoutDir.mkdir();
        }
        for(String file :files){
            BufferedImage image = ImageIO.read(new java.io.File(file));
			if(image==null){
				System.err.println("error read:"+file);
				continue;
			}
            //image=resize(image);
            
			if(split){
				if(image.getWidth()*1.0f/image.getHeight()>=basicSize.getWidth()*1.0f/basicSize.getHeight()*1.6f){		
					if(heightm){
						BufferedImage i=crop(image,0,0,image.getWidth()/2,image.getHeight(),
							(int)(image.getWidth()/2*basicSize.getHeight()/image.getHeight()),(int)basicSize.getHeight());
						addImagePage(pdDocument,i);
						BufferedImage j=crop(image,image.getWidth()/2,0,image.getWidth()/2,image.getHeight(),
							(int)(image.getWidth()/2*basicSize.getHeight()/image.getHeight()),(int)basicSize.getHeight());
						addImagePage(pdDocument,j);
						if(imageout){
							ImageIO.write(i,"png", new File((imgout_index++)+".png",imgoutDir.getAbsolutePath()));
							ImageIO.write(j,"png", new File((imgout_index++)+".png",imgoutDir.getAbsolutePath()));
						}
					}else{
						BufferedImage i=crop(image,0,0,image.getWidth()/2,image.getHeight(),image.getWidth()/2,image.getHeight());
						BufferedImage j=crop(image,image.getWidth()/2,0,image.getWidth()/2,image.getHeight(),image.getWidth()/2,image.getHeight());
						addImagePage(pdDocument,i);
						addImagePage(pdDocument,j);
						if(imageout){
							ImageIO.write(i,"png", new File((imgout_index++)+".png",imgoutDir.getAbsolutePath()));
							ImageIO.write(j,"png", new File((imgout_index++)+".png",imgoutDir.getAbsolutePath()));
						}
					}
					
					continue;
				}
			}
			BufferedImage i;
			if(heightm){
				i=crop(image,0,0,image.getWidth(),image.getHeight(),(int)(image.getWidth()*basicSize.getHeight()/image.getHeight()),(int)basicSize.getHeight());				
			}else{
				i=image;
			}
			addImagePage(pdDocument,i);
			if(imageout){
				ImageIO.write(i,"png", new File((imgout_index++)+".png",imgoutDir.getAbsolutePath()));
			}			
        }
        pdDocument.save(pdfPath);
        pdDocument.close();
    }
    
	
	public  BufferedImage crop(BufferedImage img,int x,int y,int w,int h,int dw,int dh) {
        try {		  
          BufferedImage dimg = new BufferedImage(dw, dh, img.getType());
          Graphics2D g = dimg.createGraphics();
          g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
          g.drawImage(img, 0, 0, dw, dh, x, y, x+w, y+h, null);
          g.dispose();
          return dimg;
        } catch (Exception e) {
          e.printStackTrace();
          return img;
        }
    }
	
    
    public  BufferedImage resize(BufferedImage img) {
        try {
          int w = img.getWidth();
          int h = img.getHeight();
          
          int newWidth=w/2;
          int newHeight=h/2;
          BufferedImage dimg = new BufferedImage(newWidth, newHeight, img.getType());
          Graphics2D g = dimg.createGraphics();
          g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
          g.drawImage(img, 0, 0, newWidth, newHeight, 0, 0, w, h, null);
          g.dispose();
          return dimg;
        } catch (Exception e) {
          e.printStackTrace();
          return img;
        }
    }

    public  boolean resize(String src,String to,int newWidth,int newHeight) {
        try {
          File srcFile = new File(src);
          File toFile = new File(to);
          BufferedImage img = ImageIO.read(srcFile);
          int w = img.getWidth();
          int h = img.getHeight();
          BufferedImage dimg = new BufferedImage(newWidth, newHeight, img.getType());
          Graphics2D g = dimg.createGraphics();
          g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
          g.drawImage(img, 0, 0, newWidth, newHeight, 0, 0, w, h, null);
          g.dispose();
          ImageIO.write(dimg, "jpg", toFile);
        } catch (Exception e) {
          e.printStackTrace();
          return false;
        }
        return true;
    }
	
	
	
	 int pdf2img() throws IOException {
        try{
            InputStream stream = new FileInputStream(this.pdf_file);
            PDDocument doc = PDDocument.load(stream);
            PDFRenderer pdfRenderer = new PDFRenderer(doc);
            PDPageTree pages = doc.getPages();
            int pageCount = pages.getCount();
            for (int i = 0; i < pageCount; i++) {
                BufferedImage bim = pdfRenderer.renderImageWithDPI(i, 200);
                FileOutputStream os = new FileOutputStream(this.image_dir+"/"+i);
                ImageIO.write(bim, "jpg",os);
                os.close();
            }
            stream.close();
        }catch(Exception e){
            e.printStackTrace();
            return -1;
        }
        return 0;
    }
}