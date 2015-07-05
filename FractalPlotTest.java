/*

Helsingin yliopisto, Tietojenk‰sittelytieteen laitos
Ohjelmoinnin harjoitustyˆ, kev‰t 2004
Jussi Jousimo

*/

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;

/* P‰‰luokka, josta ohjelman suoritus alkaa. */
public class FractalPlotTest extends java.applet.Applet {
	/* Ohjausikkunaolio */
    private ControlFrame control;

	/* K‰ynnist‰‰ sovelman avaamalla ohjausikkunan. */
    public void start() {
        super.start();
		control=new ControlFrame("Fractal Plot");
		control.setVisible(true);
    }

	/* K‰ynnist‰‰ sovelluksen. Metodi kutsuu solveman k‰ynnistysmetodia,
	   sill‰ sovellus toimii samalla koodilla kuin sovelmakin. */
    public static void main(String[] args) {
		FractalPlot applet=new FractalPlot();
		applet.init();
		applet.start();
    }
}

/* Luokka kompleksilukujen k‰sittelyyn. Luokka on vain t‰m‰n ohjelman
   sis‰iseen k‰yttˆˆn, joten se toteuttaa vain tarvittavat toiminnot. */
class Complex {
	/* Kompleksiluvun reaali- ja imagin‰‰riosat */
    public double re, im;

    public Complex() {}

    public Complex(Complex c) {
		re=c.re;
		im=c.im;
    }
	
    public Complex(double re, double im) {
		this.re=re;
		this.im=im;
    }

	public Complex copy() {
		return new Complex(re, im);
	}

	public String toString() {
		return "("+re+", "+im+")";
	}

	/* Laskee kompleksivektorin pituuden */
    public double abs() {
		return java.lang.Math.sqrt(re*re+im*im);
	}

	/* Laskee kaksi kompleksilukua yhteen */
    public Complex add(Complex c) {
		re+=c.re;
		im+=c.im;
		return this;
    }

	/* Korottaa toiseen */
    public Complex pow2() {
		double temp=re*re-im*im;
		im=2*re*im;
		re=temp;
		return this;
    }
}

/* Luokka Fractal on abstrakti yliluokka kaikille eri tyyppisille
   fraktaaleille. Se tarjoaa mm. koordinaattien muunnosmetodit 
   piirtoalueen sek‰ fraktaalitason v‰lill‰ ja piirtos‰ikeen k‰sittelyn. 
   Itse piirt‰minen tapahtuu luokan perillisess‰. Piirtos‰ikeit‰ voi olla
   aktiivisena vain yksi kerralla, josta johtuen s‰ieolio on staattinen. */
abstract class Fractal implements Runnable { 
	/* N‰kyv‰n alueen koordinaatit fraktaalitasossa. */
    public Complex min, max;
	/* Maksimi-iteraatioiden m‰‰r‰ */
    public int maxIters;
	/* Piirto-oliot ruudulle ja kuvalle */
	protected Graphics screen, image;
	/* Piirtos‰ieolio */
	protected static Thread plotThread;
	/* Kertoo s‰ikeelle halutaanko piirto keskeytt‰‰. */
	protected static boolean stopPlot=false;
	/* 16 v‰rin peruspaletti */
    protected final Color PALETTE16[]={
		new Color(0, 0, 0), new Color(0, 0, 170), 
		new Color(0, 170, 0), new Color(0, 170, 170), 
		new Color(170, 0, 0), new Color(170, 0, 170),
		new Color(170, 85, 0), new Color(170, 170, 170), 
		new Color(85, 85, 85), new Color(85, 85, 255),
		new Color(85, 255, 85), new Color(85, 255, 255),
		new Color(255, 85, 85), new Color(255, 85, 255),
		new Color(255, 255, 85), new Color(255, 255, 255)
    };

    public Fractal(Complex min, Complex max, int maxIters) throws Exception {
System.out.println("Fractal.Fractal()");
		if (min.re>=max.re || min.im>=max.im) 
			throw new Exception("Internal error: invalid arguments!");
		this.min=min;
		this.max=max;
		this.maxIters=maxIters;
    }

	/* Palauttaa identtisen kopio itsest‰‰n. M‰‰ritelt‰v‰ periviss‰
	   luokissa. */
	abstract public Fractal copy() throws Exception;

	/* Laskee kompleksisen skaalausvakion, jolla voidaan muuttaa piirtoalueen
	   koordinaatin fraktaalitason koordinaateiksi. */
    protected Complex getScale(int width, int height) throws Exception {
System.out.println("Fractal.getScale()");
		if (width==0 || height==0)
			throw new Exception("Internal error: invalid arguments!");
		return new Complex((max.re-min.re)/width, 
						   (max.im-min.im)/height);
    }

	/* Muuttaa piirtoalueen koordinaatit fraktaalitason koordinaateiksi. */
    public Complex translate(int x, int y, int width, int height) 
		throws Exception {
System.out.println("Fractal.translate()");
		Complex scale=getScale(width, height);
		return new Complex(x*scale.re+min.re, y*scale.im+min.im);
    }
	
	/* Alustaa piirtos‰ikeen ja k‰ynnist‰‰ sen. Odottaa tarvittaessa kunnes
	   edellinen s‰ie on kuollut. */
    public void plot(Graphics screen, Graphics image) /*throws Exception*/ {
System.out.println("Fractal.plot()");

		/* S‰ikeen varsinainen piirtometodi tarvitsee tiedon mihin fraktaali
		   piirret‰‰n (ruudulle ja kuvaan) ja se v‰litet‰‰n t‰t‰ kautta.
		   screen-oliolle t‰ytyy m‰‰ritell‰ piirtoalue (clip bound), 
		   jotta piirtometodi tiet‰isi mihin kohtaan fraktaali
		   tulee piirt‰‰. T‰ss‰ oletetaan, ett‰ ruudun piirrett‰v‰ alue on
		   (v‰hint‰‰n) yht‰ suuri kuin kuva. */
		this.screen=screen;
		this.image=image;

		/* Extratarkistus */
		if (stopPlot==true && doesPlot()==false) {
System.out.println("Fractal.plot(): if (stopPlot==true && doesPlot()==false)");
			stopPlot=false;
		}

		/* Vain yksi piirtos‰ie saa olla aktiivinen kerrallaan, joten
		 odotetaan ensin, ett‰ edellinen piirtos‰ie kuolee. */
		if (stopPlot==true) {
System.out.println("Fractal.plot(): if (stopPlot==true)");
			try {
				plotThread.join();
			}
			catch (InterruptedException x) {}
			stopPlot=false;
		}

		/* Alustaa piirtos‰ikeen ja k‰ynnist‰‰ sen. */
		plotThread=new Thread(this);
		plotThread.start();
    }

	/* Kertoo piirtos‰ikelle, ett‰ piirt‰minen tulee keskeytt‰‰. */
	public static void stopPlot() {
System.out.println("Fractal.stopPlot()");
		stopPlot=true;
	}

	/* Kertoo onko piirtos‰ie toiminnassa. */
	public static boolean doesPlot() {
System.out.println("Fractal.doesPlot()");
		if (plotThread==null) {
System.out.println("Fractal.doesPlot(): if (plotThread==null)");
			return false;
		}
		return plotThread.isAlive();
	}
} 

/* Luokka, joka piirt‰‰ Mandelbrotin joukon. */
class Mandelbrot extends Fractal {
	/* Alue, jonka ulkopuolella iterointi ei en‰‰ suppene. */
    private final double DISTANCE=2.0;

    public Mandelbrot(Complex min, Complex max, int maxIters) 
		throws Exception {
		super(min, max, maxIters);
System.out.println("Mandelbrot.Mandelbrot()");
    }

	public Fractal copy() throws Exception {
System.out.println("Mandelbrot.copy()");
		return new Mandelbrot(min.copy(), max.copy(), maxIters);
	}

	/* S‰iemetodi, jossa fraktaalin piirt‰minen tapahtuu. */
	public void run() {
System.out.println("Mandelbrot.run()");
		try {
			if (screen==null || image==null)
				throw new Exception("Internal error: invalid call!\n");

			/* Alue piirtoikkunassa, johon fraktaali piirret‰‰n. */
			Rectangle paintArea=screen.getClipBounds();
			/* Skaalausvakio, jolla ruudun koordinaatit muutetaan
			   fraktaalin koordinaateiksi. */
			Complex scale=getScale(paintArea.width, paintArea.height);
			/* Fraktaalitason piste, jota tutkitaan iteroimalla kuuluuko
			   se Mandelbrotin joukkoon. */
			Complex pos=new Complex(min);

			/* Kuvan piirt‰minen tapahtuu etenem‰ll‰ fraktaalitason
			   minimikoordinaatista (=kuvan vasen yl‰reunus) l‰htien
			   reaaliakselilla positiiviseen suuntaan ja imagin‰‰riakselilla
			   negatiiviseen suunta maksimikoordinaattiin asti. Alueen 
			   jokaisessa pisteess‰ tutkitaan kuuluuko se Mandelbrotin
			   joukkoon. */
			for (int y=0; y<paintArea.height; y++) {
				/* Palataan reaaliakselin alkuun. */
				pos.re=min.re;

				for (int x=0; x<paintArea.width; x++) {
					/* Asetetaan iteroitava funktio nollaksi. */
					Complex z=new Complex(0, 0);
					/* Iteraatioiden m‰‰r‰. */
					int n;

					/* Iterointi katkaistaan, kun saavutetaan
					   maksimi-iteraatioiden m‰‰r‰ tai iterointivektori
					   kasvaa tarpeeksi pitk‰ksi. */
					for (n=0; n<maxIters && z.abs()<DISTANCE; n++) {
						z.pow2().add(pos); /* z=z*z+pos */
					}

					/* Keskeytt‰‰ tarvittaessa piirron. */
					if (stopPlot==true) {
System.out.println("Mandelbrot.run(): if (stopPlot==true)");
						throw new InterruptedException();
					}
					
					/* Iterointi hajaantui => piste ei ole Mandelbrotin
					   joukossa, joten v‰ritet‰‰n se suhteessa iterointien
					   m‰‰r‰‰n. */
					if (n<maxIters) {
						screen.setColor(PALETTE16[n%16]);
						image.setColor(PALETTE16[n%16]);
					}
					/* Iterointi suppeni => piste on Mandelbrotin joukossa,
					   joten v‰ritet‰‰n se mustalla. */
					else {
						screen.setColor(Color.black);
						image.setColor(Color.black);
					}

					/* Piirret‰‰n piste sek‰ ruudulle ett‰ kuvaan. */
					screen.drawLine(paintArea.x+x, paintArea.y+y, 
									paintArea.x+x+1, paintArea.y+y+1);
					image.drawRect(x, y, x+1, y+1);

					/* Edet‰‰n reaaliakaselilla. */
					pos.re+=scale.re;
				}

				/* Edet‰‰n imagin‰‰riakselilla. */
				pos.im+=scale.im;
			}
		}
		catch (InterruptedException x) {
			/* Piirto keskeytyi */
		}
		catch (Exception x) {
			System.out.println(x.getMessage());
		}
	}
}

/* Luokka, joka piirt‰‰ Julian joukon. */
class Julia extends Fractal {
	/* Alue, jonka ulkopuolella iterointi ei en‰‰ suppene. */
    private final double DISTANCE=2.0;
	/* Vakio Julian joukon piirt‰mist‰ varten. */
    public Complex c;

    public Julia(Complex min, Complex max, Complex c, int maxIters)
		throws Exception {
		super(min, max, maxIters);
		this.c=c; 
System.out.println("Julia.Julia()");
    }

	public Fractal copy() throws Exception {
System.out.println("Julia.copy()");
		return new Julia(min.copy(), max.copy(), c.copy(), maxIters);
	}

	public void run() {
System.out.println("Julia.run()");
		try {
			if (screen==null || image==null)
				throw new Exception("Internal error: invalid call!\n");

			Rectangle paintArea=screen.getClipBounds();
			Complex scale=getScale(paintArea.width, paintArea.height);
			Complex pos=new Complex(min);

			/* Piirt‰minen tapahtuu vastaavalla tavalla kuin 
			   Mandelbrot-luokassa. Ainoa ero on, ett‰ iteroitava funktio
			   alustetaan iteroitavalla pisteell‰ ja iteroitavaan pisteen
			   sijaan funktioon lis‰t‰‰n Julian vakio, c. */
			for (int y=0; y<paintArea.height; y++) {
				pos.re=min.re;

				for (int x=0; x<paintArea.width; x++) {
					/* Alustetaan iteroitava funktio iterointipisteell‰. */
					Complex z=new Complex(pos);
					int n;
					
					for (n=0; n<maxIters && z.abs()<DISTANCE; n++) {
						z.pow2().add(c); /* z=z*z+c */
					}

					if (stopPlot==true) {
System.out.println("Julia.run(): if (stopPlot==true)");
						throw new InterruptedException();
					}
					
					if (n<maxIters) {
						screen.setColor(PALETTE16[n%16]);
						image.setColor(PALETTE16[n%16]);
					}
					else {
						screen.setColor(Color.black);
						image.setColor(Color.black);
					}
					screen.drawLine(paintArea.x+x, paintArea.y+y, 
									paintArea.x+x+1, paintArea.y+y+1);
					image.drawLine(x, y, x+1, y+1);

					pos.re+=scale.re;
				}

				pos.im+=scale.im;
			}
		}
		catch (InterruptedException x) {
		}
		catch (Exception x) {
			System.out.println(x.getMessage());
		}
	}
}

class InputException extends Exception {
	public InputException(String message) {
		super(message);
System.out.println("InputException.InputException()");
	}
}

/* Ilmoitusikkuna. Luo erillisen ikkunan, jossa esitt‰‰ ilmoituksen
   ja ok-napin, jolla ikkuna sulkeutuu. */
class MessageDialog extends Dialog implements ActionListener {
	/* Piirt‰‰ ikkunan ja lis‰‰ tapahtumank‰sittelij‰n ok-nappiin. */
    public MessageDialog(Frame parent, String title, String message) {
		super(parent, title, true);
System.out.println("MessageDialog.MessageDialog()");
		this.add("Center", new Label(message));
		Panel panel=new Panel();
		panel.setLayout(new FlowLayout());
		Button buttonOk=new Button("Ok");
		buttonOk.addActionListener(this);
		panel.add(buttonOk);
		this.add("South", panel);
		this.setSize(300, 100);
		this.setLocation(400, 220);
		this.pack();
		this.setVisible(true);
    }
	
	/* K‰ytt‰j‰ painoi ok-nappia tai jotain muuta nappia, mik‰ sulkee
	   ikkunan. */
    public void actionPerformed(ActionEvent e) {
System.out.println("MessageDialog.actionPerformed()");
		this.setVisible(false);
		this.dispose();
    }
}

/* Ohjausikkuna, jossa sijaitsevat kontrollit fraktaalin piirt‰mist‰
   varten. */
class ControlFrame extends Frame implements ActionListener, ItemListener, 
											WindowListener {
	/* Piirtoikkuna. */
    private PlotFrame plot;
	/* K‰yttˆliittym‰komponentit */
    private CheckboxGroup groupType;
    private Checkbox checkMandelbrot, checkJulia;
    private TextField textMinx, textMiny, textMaxx, textMaxy;
    private Label labelJuliax, labelJuliay;
    private TextField textJuliax, textJuliay, textIters;
    private Button buttonDrawCurrent, buttonZoomOut, buttonDrawPrevious;
    private Button buttonExit;
	/* Nykyinen n‰kyv‰ fraktaali ja sit‰ edelt‰nyt fraktaali. */
	private Fractal currentFractal, previousFractal;

    public ControlFrame(String title) {
		super(title);

System.out.println("ControlFrame.ControlFrame()");

		addWindowListener(this);

		/* Ohjausikkuna on jaettu 6 x 4 ruutuihin, joihin
		   k‰yttˆliittym‰komponentit sijoitetaan. */
		setLayout(new GridLayout(6, 4));

		/* Luo komponentit ja lis‰‰ tapahtumak‰sittelij‰t niihin. */
		groupType=new CheckboxGroup();
		add(new Label("Fractal type:"));
		add(checkMandelbrot=new Checkbox("Mandelbrot", groupType, true));
		checkMandelbrot.addItemListener(this);
		add(checkJulia=new Checkbox("Julia", groupType, false));
		checkJulia.addItemListener(this);
		add(new Label());      
		
		add(new Label("Zoom min x: "));
		add(textMinx=new TextField("-2.0"));
		add(new Label("Zoom max x: "));
		add(textMaxx=new TextField("2.0"));
		
		add(new Label("Zoom min y: "));
		add(textMiny=new TextField("-2.0"));
		add(new Label("Zoom max y: "));
		add(textMaxy=new TextField("2.0"));
		
		add(labelJuliax=new Label("Julia x:"));
		add(textJuliax=new TextField("0"));
		add(labelJuliay=new Label("Julia y:"));
		add(textJuliay=new TextField("0"));
		
		labelJuliax.setEnabled(false);
		labelJuliay.setEnabled(false);
		textJuliax.setEnabled(false);
		textJuliay.setEnabled(false);
	
		add(new Label("Max iterations: "));
		add(textIters=new TextField("500"));
		add(new Label());
		add(new Label());
		
		add(buttonDrawCurrent=new Button("Draw Current"));
		buttonDrawCurrent.addActionListener(this);
		add(buttonDrawPrevious=new Button("Draw Previous"));
		buttonDrawPrevious.addActionListener(this);
		buttonDrawPrevious.setEnabled(false);
		add(buttonZoomOut=new Button("Zoom Out"));
		buttonZoomOut.addActionListener(this);
		add(buttonExit=new Button("Exit"));
		buttonExit.addActionListener(this);
		
		/* Asettaa ikkunan koon komponenttien mukaan. */
		pack();
		setLocation(400, 30);

		/* Hakee arvot ohjausikkunasta ja piirt‰‰ Mandelbrotin. */
		try {
			currentFractal=previousFractal=getMandelbrot();
		}
		catch (InputException x) {
			System.out.println("Internal error: invalid initial values!");
			return;
		}
		catch (Exception x) {
			System.out.println(x.getMessage());
		}

		plot=new PlotFrame(this, "Mandelbrot");
		plot.plot(currentFractal);
    }

	/* Palauttaa k‰ytt‰j‰n syˆtt‰m‰t zoomauskoordinaatit. Jos syˆte on
	   virheellinen, heitt‰‰ poikkeuksen. */
	private void getZoomMinMax(Complex min, Complex max)
		throws InputException, Exception {

System.out.println("ControlFrame.getZoomMinMax()");

		if (min==null || max==null)
			throw new Exception("Internal error: invalid arguments!");

		try {
			min.re=Double.valueOf(textMinx.getText()).doubleValue();
			min.im=Double.valueOf(textMiny.getText()).doubleValue();
			max.re=Double.valueOf(textMaxx.getText()).doubleValue();
			max.im=Double.valueOf(textMaxy.getText()).doubleValue();
		}
		catch (Exception x) {
			throw new InputException("Invalid value for zoom coordinates!");
		}

		if (min.re>=max.re || min.im>=max.im) 
			throw new InputException("Zoom coordinates must be min < max!");
	}

	/* Palauttaa k‰ytt‰j‰n syˆtt‰m‰n maksimi-iteraatioiden m‰‰r‰n. Jos
	   syˆte on virheellinen, heitt‰‰ poikkeuksen. */
	private int getMaxIters() throws InputException {
System.out.println("ControlFrame.getMaxIters()");

		int maxIters;

		try {
			maxIters=Integer.valueOf(textIters.getText()).intValue();
		}
		catch (Exception x) {
			throw new InputException("Invalid value for max iterations!");
		}

		if (maxIters<1) 
			throw new InputException("Max iterations must be > 0!");

		return maxIters;
	}

	/* Palauttaa k‰ytt‰j‰n syˆtt‰m‰n Julian vakion. Jos syˆte on virheellinen,
	   heitt‰‰ poikkeuksen. */
	private Complex getJuliaC() throws InputException {
System.out.println("ControlFrame.getJuliaC()");

		Complex juliaC=new Complex();

		try {
			juliaC.re=Double.valueOf(textJuliax.getText()).doubleValue();
			juliaC.im=Double.valueOf(textJuliay.getText()).doubleValue();
		}
		catch (Exception x) {
			throw new InputException("Invalid value for Julia x/y!");
		}

		return juliaC;
	}

	/* Luo Mandelbrot-olion k‰ytt‰j‰n syˆtt‰mist‰ tiedoista. */
	private Mandelbrot getMandelbrot() throws InputException, Exception {
System.out.println("ControlFrame.getMandelbrot()");

		Complex min=new Complex(), max=new Complex();
		int maxIters;
		getZoomMinMax(min, max);
		maxIters=getMaxIters();
		return new Mandelbrot(min, max, maxIters);
	}

	/* Luo Julia-olion k‰ytt‰j‰n syˆtt‰mist‰ tiedoista. */
	private Julia getJulia() throws InputException, Exception {
System.out.println("ControlFrame.getJulia()");

		Complex min=new Complex(), max=new Complex(), juliaC;
		int maxIters;
		getZoomMinMax(min, max);
		juliaC=getJuliaC();
		maxIters=getMaxIters();
		return new Julia(min, max, juliaC, maxIters);
	}

	/* Kopio nykyisen fraktaalin edelliseen ja asettaa Draw Previous-
	   painikkeen aktiiviseksi. */
	public void setPrevious() throws Exception {
System.out.println("ControlFrame.setPrevious()");

		previousFractal=currentFractal.copy();
		buttonDrawPrevious.setEnabled(true);
	}

	/* Asettaa Julian vakion ohjausikkunaan. */
	public void setJuliaC(Complex c) {
System.out.println("ControlFrame.setJuliaC()");

		textJuliax.setText(""+c.re);
		textJuliay.setText(""+c.im);
	}

	/* Asettaa zoom-koordinaatit ohjausikkunaan. */
	public void setZoomMinMax(Complex min, Complex max) {
System.out.println("ControlFrame.setZoomMinMax()");

		textMinx.setText(""+min.re);
		textMiny.setText(""+min.im);
		textMaxx.setText(""+max.re);
		textMaxy.setText(""+max.im);
	}

	/* Asettaa Mandelbrotin aktiiviseksi ohjausikkunaan. */
	private void setMandelbrot() {
System.out.println("ControlFrame.setMandelbrot()");

		groupType.setSelectedCheckbox(checkMandelbrot);
		labelJuliax.setEnabled(false);
		labelJuliay.setEnabled(false);
		textJuliax.setEnabled(false);
		textJuliay.setEnabled(false);
	}

	/* Asettaa Julian aktiiviseksi ohjausikkunaan. */
	private void setJulia() {
System.out.println("ControlFrame.setJulia()");

		groupType.setSelectedCheckbox(checkJulia);
		labelJuliax.setEnabled(true);
		labelJuliay.setEnabled(true);
		textJuliax.setEnabled(true);
		textJuliay.setEnabled(true);
	}
	
	/* Tapahtumak‰sittelij‰ fraktaalityypin vaihtopainikkeille. */
    public void itemStateChanged(ItemEvent e) {
System.out.println("ControlFrame.itemStateChanged()");

		/* Asettaa aktiviiseksi valitun fraktaalin tyyppiin liittyv‰ti
		   komponentit ohjausikkunassa. */
		Object o=e.getSource();
		if (o==checkMandelbrot) {
System.out.println("ControlFrame.itemStateChanged(): if (o==checkMandelbrot)");

			setMandelbrot();
		}
		else if (o==checkJulia) {
System.out.println("ControlFrame.itemStateChanged(): else if (o==checkJulia)");

			setJulia();
		}
    }
	
	/* Tapahtumak‰sittelij‰ painikkeille. */
    public void actionPerformed(ActionEvent e) {
System.out.println("ControlFrame.actionPerformed()");

		try {
			Object o=e.getSource();
			
			/* K‰ytt‰j‰ painoi Exit-nappia. */
			if (o==buttonExit) {
System.out.println("ControlFrame.actionPerformed(): if (o==buttonExit)");
				plot.setVisible(false);
				plot.dispose();
				setVisible(false);
				dispose();
			}
			
			/* K‰ytt‰j‰ painoi Draw Current-nappia. Piirt‰‰ valittuna olevan
			   fraktaalin k‰ytt‰j‰n antaman syˆtteen perusteella. */
			else if (o==buttonDrawCurrent) {
System.out.println("ControlFrame.actionPerformed(): else if (o==buttonDrawCurrent)");

				/* Mandelbrot aktiivisena */
				if (groupType.getSelectedCheckbox()==checkMandelbrot) {
System.out.println("ControlFrame.actionPerformed(): if (groupType.getSelectedCheckbox()==checkMandelbrot)");

					/* Ottaa talteen edellisen piirretyn fraktaalin. */
					previousFractal=currentFractal;
					currentFractal=getMandelbrot();
					buttonDrawPrevious.setEnabled(true);
					
					/* Jos edellist‰ fraktaalia viel‰ piirret‰‰n, keskeytet‰‰n
					   sen piirto. */
					Fractal.stopPlot();
					plot.setTitle("Mandelbrot");
					plot.plot(currentFractal);
				}
				
				/* Julia aktiivisena */
				else if (groupType.getSelectedCheckbox()==checkJulia) {
System.out.println("ControlFrame.actionPerformed(): else if (groupType.getSelectedCheckbox()==checkJulia)");

					previousFractal=currentFractal;
					currentFractal=getJulia();
					buttonDrawPrevious.setEnabled(true);
					Fractal.stopPlot();
					plot.setTitle("Julia");
					plot.plot(currentFractal);
				}
			}

			/* K‰ytt‰j‰ painoi Zoom Out-nappia. */
			else if (o==buttonZoomOut) {
System.out.println("ControlFrame.actionPerformed(): else if (o==buttonZoomOut)");

				/* Kopio vanhan fraktaalin talteen. */
				setPrevious();
				
				/* Yhdeks‰nkertaistaa piirrett‰v‰n alueen. */
				Complex len=new Complex((currentFractal.max.re-
										 currentFractal.min.re),
										 (currentFractal.max.im-
										 currentFractal.min.im));
				currentFractal.min.re-=len.re;
				currentFractal.max.re+=len.re;
				currentFractal.min.im-=len.im;
				currentFractal.max.im+=len.im;
				
				/* P‰ivitt‰‰ uudet koordinaatit ohjausikkunaan ja piirt‰‰ 
				   fraktaalin. Keskeytt‰‰ edellisen piirt‰misen. */
				setZoomMinMax(currentFractal.min, currentFractal.max);
				Fractal.stopPlot();
				plot.plot(currentFractal);
			}
			
			/* K‰ytt‰j‰ painoi Draw Previous-nappia. */
			else if (o==buttonDrawPrevious) {
System.out.println("ControlFrame.actionPerformed(): else if (o==buttonDrawPrevious)");

				/* Vaihtaa nykyisen ja edellisen piirretyn fraktaalin 
				   j‰rjestyst‰. */
				Fractal temp=currentFractal;
				currentFractal=previousFractal;
				previousFractal=temp;
				Fractal.stopPlot();
				
				/* P‰ivitt‰‰ fraktaalin tiedot ohjaus- sek‰ piirtoikkunaan ja 
				   lopuksi piirt‰‰ sen. */
				if (currentFractal instanceof Mandelbrot) {
System.out.println("ControlFrame.actionPerformed(): if (currentFractal instanceof Mandelbrot)");
					setMandelbrot();
					plot.setTitle("Mandelbrot");
				}
				else if (currentFractal instanceof Julia) {
System.out.println("ControlFrame.actionPerformed(): else if (currentFractal instanceof Julia)");
					setJulia();
					plot.setTitle("Julia");
				}
				plot.plot(currentFractal);
			}
		}
		catch (InputException x) {
			/* K‰ytt‰j‰n syˆtteess‰ on virheit‰. */
			MessageDialog dialog=new MessageDialog(this, 
												   "User Input Error", 
												   x.getMessage());
		}
		catch(Exception x) {
			System.out.println(x.getMessage());
		}
    }
	
	/* Tapahtumak‰sittelij‰ ikkunalle. Sulkee sek‰ piirto- ett‰
	   ohjausikkunan, kun k‰ytt‰j‰ painaa ohjausikkunan sulkemisnappia. */
    public void windowClosing(WindowEvent e) {
System.out.println("ControlFrame.windowClosing()");
		plot.setVisible(false);
		plot.dispose();
		setVisible(false);
		dispose();
    }
	
    public void windowClosed(WindowEvent e) {}
    public void windowOpened(WindowEvent e) {}
    public void windowIconified(WindowEvent e) {}
    public void windowDeiconified(WindowEvent e) {}
    public void windowActivated(WindowEvent e) {}
    public void windowDeactivated(WindowEvent e) {}
}

/* Piirtoikkuna, johon fraktaali piirret‰‰n. Toteuttaa myˆs k‰yttˆliittym‰n,
   jonka avulla fraktaalia voi zoomata ja Julian joukon vakion voi poimia
   hiirell‰. */
class PlotFrame extends Frame implements WindowListener, MouseListener, 
										 MouseMotionListener, 
										 ComponentListener {
	/* Viittaus ohjausikkunaan sen tietojen p‰ivitt‰mist‰ varten */
	private ControlFrame owner;
	/* Viittaus ohjausikkunassa olevaan fraktaaliolioon, currentFractal */
    private Fractal fractal;
	/* Erillinen kuva fraktaalista uudelleenpiirt‰mist‰ varten */
	private Image image;
	/* Piirtoikkunan piirtoalue */
	private Rectangle paintArea;
	/* Hiiren kursorin koordinaatit zoomauskehikon laskemista varten */
    private Point mousePosition, currentPosition;
	/* Zoomauskehikon koordinaatit */
    private int x0, y0, x1, y1;
	
    public PlotFrame(ControlFrame owner, String title) {
		super(title);
System.out.println("PlotFrame.PlotFrame()");
		this.owner=owner;

		addWindowListener(this);
		addMouseListener(this);
		addMouseMotionListener(this);
		addComponentListener(this);

		setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));

		setSize(300, 300);
		setLocation(30, 30);
    }

	/* Laskee ikkunassa olevan piirtoalueen paikan ja koon. */
	protected Rectangle getPaintArea() {
System.out.println("PlotFrame.getPaintArea()");
		Dimension d=getSize();
		Insets i=getInsets();
		return new Rectangle(i.left, i.top, d.width-i.left-i.right, 
							 d.height-i.top-i.bottom);
	}

	/* Piirt‰‰ uuden fraktaalin. */
    public void plot(Fractal fractal) {
System.out.println("PlotFrame.plot()");
		this.fractal=fractal;

		/* Jos ikkuna ei ole n‰kyviss‰, avataan se, lasketaan sille
		   piirtoalue ja luodaan muistialue piirrett‰v‰lle kuvalle. */
        if (isVisible()==false) {
System.out.println("PlotFrame.plot(): if (isVisible()==false)");
			setVisible(true);
		}
		if (paintArea==null || image==null) { 
System.out.println("PlotFrame.plot(): if (paintArea==null || image==null)");
			paintArea=getPaintArea();
			image=createImage(paintArea.width, paintArea.height);
		}

		//setCursor(new Cursor(Cursor.WAIT_CURSOR));

		Graphics screen=getGraphics();
		/* Asetetaan piirtoalue, johon fraktaali piirret‰‰n. */
		screen.setClip(paintArea);

		try {
			fractal.plot(screen, image.getGraphics());
		}
		catch (Exception x) {
			System.out.println(x.getMessage());
			return;
		}

		//setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
    }

	/* P‰ivitt‰‰ ikkunassa olevan fraktaalin kokonaan tai osittain. 
	   T‰t‰ kutsutaan alunperin k‰yttˆj‰rjestelm‰st‰. */
	public void paint(Graphics g) {
System.out.println("PlotFrame.paint()");
 		if (paintArea==null || image==null) {
System.out.println("PlotFrame.paint(): if (paintArea==null || image==null)");
			return;
		}
  		g.drawImage(image, paintArea.x, paintArea.y, this);
	}
	
	/* P‰ivitt‰‰ ikkunaa. Kutsutaan k‰yttˆj‰rjestelm‰st‰. */
	public void update(Graphics g) {
System.out.println("PlotFrame.update()");
		update(g);
	}

	//public void repaint() {}

	/* Tapahtumak‰sittelij‰ ikkunalle. Sulkee ikkunan, mutta ei poista sit‰. */
    public void windowClosing(WindowEvent e) {
System.out.println("PlotFrame.windowClosing()");
		setVisible(false);
    }

    public void windowClosed(WindowEvent e) {}
    public void windowOpened(WindowEvent e) {}
    public void windowIconified(WindowEvent e) {}
    public void windowDeiconified(WindowEvent e) {}
    public void windowActivated(WindowEvent e) {}
    public void windowDeactivated(WindowEvent e) {}

	/* Tapahtumak‰sittelij‰ hiirelle. Kutsutaan, kun hiiren nappi painetaan
	   alas. */
    public void mousePressed(MouseEvent e) {
System.out.println("PlotFrame.mousePressed()");
		/* Ottaa hiiren koordinaatit talteen, jos vasenta nappia painettiin. */
		if ((e.getModifiers()&MouseEvent.BUTTON1_MASK)!=0) {
System.out.println("PlotFrame.mousePressed(): if ((e.getModifiers()&MouseEvent.BUTTON1_MASK)!=0)");
			mousePosition=e.getPoint();
		}
    }
	
	/* Tapahtumak‰sittelij‰ hiirelle. Kutsutaan, kun hiirt‰ liikutetaan
	   nappi alhaalla. */
    public void mouseDragged(MouseEvent e) {
System.out.println("PlotFrame.mouseDragged()");

		if ((e.getModifiers()&MouseEvent.BUTTON1_MASK)!=0) {
System.out.println("PlotFrame.mouseDragged(): if ((e.getModifiers()&MouseEvent.BUTTON1_MASK)!=0)");

			/* Hiirt‰ on liikutettu nappi alhaalla, joten piirret‰‰n
			   zoomauskehikko. */
			if (mousePosition!=null) {
System.out.println("PlotFrame.mouseDragged(): if (mousePosition!=null)");

				Graphics g=getGraphics();
				/* K‰‰nnet‰‰n v‰rit zoomauskehikon reunusten alla, jotta
				   se erottuisi taustastaan aina ja olisi helppo pyyhki‰ 
				   pois. */
				g.setXORMode(Color.orange);
				
				/* Pyyhit‰‰n edellinen kehikko. */
				if (currentPosition!=null) {
System.out.println("PlotFrame.mouseDragged(): if (currentPosition!=null)");
					g.drawRect(x0, y0, x1-x0, y1-y0);
				}
				
				/* Ottaa kehikon koordinaatit talteen ja vaihtaa niiden
				   paikkaa tarvittaessa, siten ett‰ p‰tee aina
				   x1>=x0 ja y1>=y0 */
				x0=mousePosition.x; 
				y0=mousePosition.y;            
				x1=e.getX();
				y1=e.getY();

				if (x1<x0) {
System.out.println("PlotFrame.mouseDragged(): if (x1<x0)");
					int temp=x0;
					x0=x1;
					x1=temp;
				}
				if (y1<y0) {
System.out.println("PlotFrame.mouseDragged(): if (y1<y0)");
					int temp=y0;
					y0=y1;
					y1=temp;
				}

				/* Pys‰ytt‰‰ kehikon reunuksilla, jotta se ei valuisi
				   piirtoalueen ulkopuolelle. */
				if (x0-paintArea.x<0) {
System.out.println("PlotFrame.mouseDragged(): if (x0-paintArea.x<0)");
					x0=paintArea.x;
				}
				if (x1-paintArea.x>=paintArea.width) {
System.out.println("PlotFrame.mouseDragged(): if (x1-paintArea.x>=paintArea.width)");
					x1=paintArea.width+paintArea.x-1;
				}
				if (y0-paintArea.y<0) {
System.out.println("PlotFrame.mouseDragged(): if (y0-paintArea.y<0)");
					y0=paintArea.y;
				}
				if (y1-paintArea.y>=paintArea.height) {
System.out.println("PlotFrame.mouseDragged(): if (y1-paintArea.y>=paintArea.height)");
					y1=paintArea.height+paintArea.y-1;
				}
				
				/* Piirt‰‰ kehikon. */
				g.drawRect(x0, y0, x1-x0, y1-y0);
			}

			currentPosition=e.getPoint();
		}
    }

	/* Tapahtumak‰sittelij‰ hiirelle. Kutsutaan, kun hiiren nappi
	   vapautetaan. */
    public void mouseReleased(MouseEvent e) {
System.out.println("PlotFrame.mouseReleased()");

		try {
			/* Zoomauskehikko on olemassa. */
			if (mousePosition!=null && currentPosition!=null) {
System.out.println("PlotFrame.mouseReleased(): if (mousePosition!=null && currentPosition!=null)");

				Fractal.stopPlot();
				
				Graphics g=getGraphics();
				
				/* Poistaa kehikon. */
				g.setXORMode(Color.orange);
				g.drawRect(x0, y0, x1-x0, y1-y0);             
				
				/* ƒl‰ tee mit‰‰n, jos zoomauskehikon leveys tai korkeus on 
				   nolla. */
				if (x0==x1 || y0==y1) {
System.out.println("PlotFrame.mouseReleased(): if (x0==x1 || y0==y1)");
					return;
				}
				
				/* Ottaa nykyisen fraktaalin talteen. */
				owner.setPrevious();

				/* Muuttaa kehikon koordinaatit piirtotasosta fraktaalitasoon. 
				   ja p‰ivitt‰‰ ne ohjausikkunaan. */
				Complex min=fractal.translate(x0-paintArea.x+1, 
											  y0-paintArea.y+1, 
											  paintArea.width, 
											  paintArea.height);
				Complex max=fractal.translate(x1-paintArea.x+1, 
											  y1-paintArea.y+1, 
											  paintArea.width, 
											  paintArea.height);
				owner.setZoomMinMax(min, max);
				fractal.min=min;
				fractal.max=max;
				
				/* En tied‰ miten XOR-tilan saa pois, joten nollataan
				   tilanne ja piirret‰‰n fraktaali. */
				Graphics screen=getGraphics();
				screen.setClip(paintArea);
				fractal.plot(screen, image.getGraphics());
			}
			
			/* Nappia painettiin, mutta hiirt‰ ei liikutettu. */
			else if (mousePosition!=null) {
System.out.println("PlotFrame.mouseReleased(): else if (mousePosition!=null)");
				/* P‰ivitet‰‰n tieto valitusta koordinaatista Julian vakioksi
				   ohjausikkunaan. */
				owner.setJuliaC(fractal.translate(mousePosition.x-paintArea.x
												  +1, 
												  mousePosition.y-paintArea.y
												  +1, 
												  paintArea.width, 
												  paintArea.height));
			}
			
			mousePosition=currentPosition=null;
		}
		catch(Exception x) {
			System.out.println(x.getMessage());
		}
	}
	
    public void mouseMoved(MouseEvent e) {}
    public void mouseClicked(MouseEvent e) {}
    public void mouseEntered(MouseEvent e) {}
    public void mouseExited(MouseEvent e) {}
	
	/* Tapahtumak‰sittelij‰ ikkunalle. Kutsutaan, kun ikkunan kokoa on 
	   muutettu. */
    public void componentResized(ComponentEvent e) {
System.out.println("PlotFrame.componentResized()");

		/* Tarkistaa onko ikkunan koko todellakin muuttunut.
		   K‰yttˆj‰rjestelm‰ saattaa kutsua t‰t‰ metodia, vaikka se ei
		   olisi muuttunutkaan. */
  		Rectangle newPaintArea=getPaintArea();
		if (paintArea==null) {
System.out.println("PlotFrame.componentResized(): if (paintArea==null)");
			return;
		}
		if ((paintArea.x!=newPaintArea.x || paintArea.y!=newPaintArea.y ||
			paintArea.width!=newPaintArea.width || 
			paintArea.height!=newPaintArea.height) && mousePosition==null) {
System.out.println("PlotFrame.componentResized(): if (paintArea.x!=newPaintArea.x || paintArea.y!=newPaintArea.y...");
			Fractal.stopPlot();
            paintArea=newPaintArea;
			image=createImage(paintArea.width, paintArea.height);
			plot(fractal);
		}
    }

    public void componentHidden(ComponentEvent e) {}
    public void componentMoved(ComponentEvent e) {}
    public void componentShown(ComponentEvent e) {}
}
