import java.util.TimerTask;
import java.util.Timer;

/*===============================================================*
 *  File: SWP.java                                               *
 *                                                               *
 *  This class implements the sliding window protocol            *
 *  Used by VMach class					         *
 *  Uses the following classes: SWE, Packet, PFrame, PEvent,     *
 *                                                               *
 *  Author: Professor SUN Chengzheng                             *
 *          School of Computer Engineering                       *
 *          Nanyang Technological University                     *
 *          Singapore 639798                                     *
 *===============================================================*/

public class SWP {

/*========================================================================*
 the following are provided, do not change them!!
 *========================================================================*/
   //the following are protocol constants.
   public static final int MAX_SEQ = 7; 
   public static final int NR_BUFS = (MAX_SEQ + 1)/2;

   // the following are protocol variables
   private int oldest_frame = 0;
   private PEvent event = new PEvent();  
   private Packet out_buf[] = new Packet[NR_BUFS];

   //the following are used for simulation purpose only
   private SWE swe = null;
   private String sid = null;  

   //Constructor
   public SWP(SWE sw, String s){
      swe = sw;
      sid = s;
   }

   //the following methods are all protocol related
   private void init(){
      for (int i = 0; i < NR_BUFS; i++){
	   out_buf[i] = new Packet();
      }
   }

   private void wait_for_event(PEvent e){
      swe.wait_for_event(e); //may be blocked
      oldest_frame = e.seq;  //set timeout frame seq
   }

   private void enable_network_layer(int nr_of_bufs) {
   //network layer is permitted to send if credit is available
	swe.grant_credit(nr_of_bufs);
   }

   private void from_network_layer(Packet p) {
      swe.from_network_layer(p);
   }

   private void to_network_layer(Packet packet) {
	swe.to_network_layer(packet);
   }

   private void to_physical_layer(PFrame fm)  {
      System.out.println("SWP: Sending frame: seq = " + fm.seq + 
			    " ack = " + fm.ack + " kind = " + 
			    PFrame.KIND[fm.kind] + " info = " + fm.info.data );
      System.out.flush();
      swe.to_physical_layer(fm);
   }

   private void from_physical_layer(PFrame fm) {
      PFrame fm1 = swe.from_physical_layer(); 
	fm.kind = fm1.kind;
	fm.seq = fm1.seq; 
	fm.ack = fm1.ack;
	fm.info = fm1.info;
   }


/*===========================================================================*
 	implement your Protocol Variables and Methods below: 
 *==========================================================================*/
    boolean no_nak = true;
    static boolean between(int a, int b, int c ) {
        return ((a <= b) && (b < c)) || ((c<a) && (a <= b)) || ((b < c) && (c < a));
    }
    Timer[] timer = new Timer[NR_BUFS];
    Timer ack_timer;

    /**
     * Construct and send a data, ack, or nak frame
     * @param fk             frame kind
     * @param frame_nr       frame number
     * @param frame_expected
     * @param buffer         buffer Packet(s)
     */
    void send_frame(int fk, int frame_nr, int frame_expected, Packet[] buffer) {
        PFrame s = new PFrame();
        s.kind = fk; // kind: data, ack, or nak
        if (fk == PFrame.DATA) s.info = buffer[frame_nr % NR_BUFS];
        s.seq = frame_nr; // only meaningful for data frames
        s.ack = (frame_expected + MAX_SEQ) % (MAX_SEQ + 1);
        if (fk == PFrame.NAK) no_nak = false; // one nak per frame
        to_physical_layer(s); // transmit the frame
        if (fk == PFrame.DATA) start_timer(frame_nr);
        stop_ack_timer(); // no need for separate ack frame
    }

   public void protocol6() {
        int ack_expected, next_frame_to_send, frame_expected, too_far, i;
        PFrame r = new PFrame();
        Packet[] in_buf = new Packet[NR_BUFS];
        boolean[] arrived = new boolean[NR_BUFS];
        ack_expected = 0;
        next_frame_to_send = 0;
        frame_expected = 0;
        too_far = NR_BUFS;
        for (i = 0; i < NR_BUFS; i++) arrived[i] = false;
		
		init();
        enable_network_layer(NR_BUFS); // HOW DOES enable_network_layer DO?

        while(true) {	
            wait_for_event(event);
            switch(event.type) {
                case (PEvent.NETWORK_LAYER_READY):
                    from_network_layer(out_buf[next_frame_to_send % NR_BUFS]);
                    send_frame(PFrame.DATA, next_frame_to_send, frame_expected, out_buf);
                    next_frame_to_send = (next_frame_to_send + 1) % (MAX_SEQ + 1);
                break; 

                case (PEvent.FRAME_ARRIVAL ):
                    from_physical_layer(r);
                    if (r.kind == PFrame.DATA) {
                        if ((r.seq != frame_expected) && no_nak)
                            send_frame(PFrame.NAK, 0, frame_expected, out_buf);
                        else
                            start_ack_timer(); // --> see lecture?

                        if (between(frame_expected, r.seq, too_far) && (arrived[r.seq % NR_BUFS] == false)) { // --> see lecture?
                            arrived[r.seq % NR_BUFS] = true;
                            in_buf[r.seq % NR_BUFS] = r.info;

                            // Sending to the network layer whenever the LOWER_BOUND has been received, up to the next NOT RECEIVED FRAME
                            while(arrived[frame_expected % NR_BUFS]) {
                                to_network_layer(in_buf[frame_expected % NR_BUFS]);
                                no_nak = true;
                                arrived[frame_expected % NR_BUFS] = false;
                                frame_expected = (frame_expected + 1) % (MAX_SEQ + 1);
                                too_far = (too_far + 1) % (MAX_SEQ + 1);
                                start_ack_timer(); // but why? --> see lecture?
                            }
                        }
                    }
                    if ((r.kind == PFrame.NAK) && between(ack_expected, (r.ack + 1) % (MAX_SEQ + 1), next_frame_to_send))
                        send_frame(PFrame.DATA, (r.ack + 1) % (MAX_SEQ + 1), frame_expected, out_buf);

					// Basically, when we receive r.ack, all ack between that ack_expected and next_frame_to_send
					// is assumed to be received too and hence, we can grant credit for the next packet transmission
                    while (between(ack_expected, r.ack, next_frame_to_send)) {
                        stop_timer(ack_expected);
                        ack_expected = (ack_expected + 1) % (MAX_SEQ + 1);
                        enable_network_layer(1); // TODO: FIND OUT WHY!!!! --> "DEADLOCK"; site 1 sent 4, site 2 sent 4; both can't piggyback; both send ACK due to TIMEOUT. When TIMEOUT, site 1 has received all 4 packets and frame_expected has been incremented to 3.
                    }
                break;	   
                case (PEvent.CKSUM_ERR):
                    if (no_nak) send_frame(PFrame.NAK, 0, frame_expected, out_buf);
                break;  
                case (PEvent.TIMEOUT): 
                    send_frame(PFrame.DATA, oldest_frame, frame_expected, out_buf);
                break; 
                case (PEvent.ACK_TIMEOUT): 
                    send_frame(PFrame.ACK, 0, frame_expected, out_buf);
                break; 
                default: 
                    System.out.println("SWP: undefined event type = " 
                    + event.type); 
                    System.out.flush();
            }

        }      
   }

 /* Note: when start_timer() and stop_timer() are called, 
    the "seq" parameter must be the sequence number, rather 
    than the index of the timer array, 
    of the frame associated with this timer, 
   */
 
   private void start_timer(int seq) {
        stop_timer(seq);
        timer[seq % NR_BUFS] = new Timer();
        timer[seq % NR_BUFS].schedule(new FrameTask(seq), 200); // TODO, WHY 200?
   }

   private void stop_timer(int seq) { 
        if (timer[seq % NR_BUFS] != null) {
            timer[seq % NR_BUFS].cancel();
            timer[seq % NR_BUFS] = null; 
        }
   }

   private void start_ack_timer( ) {
      stop_ack_timer();
      ack_timer = new Timer();
      ack_timer.schedule(new AckTask(), 50);
   }

   private void stop_ack_timer() {
        if (ack_timer != null) {
            ack_timer.cancel();
            ack_timer = null;
        }
   }
   class AckTask extends TimerTask {
        public void run() {
            stop_ack_timer();
            swe.generate_acktimeout_event();
        }
   }
   class FrameTask extends TimerTask {
        private int seq;
        public FrameTask(int seq) {
            this.seq = seq;
        }
        public void run() {
            stop_timer(seq);
            swe.generate_timeout_event(seq);
        }
   }
}//End of class

/* Note: In class SWE, the following two public methods are available:
   . generate_acktimeout_event() and
   . generate_timeout_event(seqnr).

   To call these two methods (for implementing timers),
   the "swe" object should be referred as follows:
     swe.generate_acktimeout_event(), or
     swe.generate_timeout_event(seqnr).
*/


