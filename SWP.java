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


    void send_frame(int fk, int frame_nr, int frame_expected, Packet[] buffer) {
        PFrame s = new PFrame();
        s.kind = fk;
        if (fk == PFrame.DATA) s.info = buffer[frame_nr % NR_BUFS];
        s.seq = frame_nr;
        s.ack = (frame_expected + MAX_SEQ) % (MAX_SEQ + 1);
        if (fk == PFrame.NAK) no_nak = false;
        to_physical_layer(s);
        if (fk == PFrame.DATA) start_timer(frame_nr);
        stop_ack_timer();
    }

   public void protocol6() {
        // Lower edge of the sender's window
        int ack_expected;               
        // Upper edge of the sender's window
        int next_frame_to_send;         
        // Lower edge of the receiver's window
        int frame_expected;             
        // Upper edge of the receiver's window
        int too_far;                                      
        // Index for looping
        int i;              
        // Create a blank frame for receiving incoming message                              
        PFrame r = new PFrame();
        // Buffers for inbound data
        Packet[] in_buf = new Packet[NR_BUFS];
        // Marking whether a data has arrived
        boolean[] arrived = new boolean[NR_BUFS];
        // Exoected frame's acknowledgment number from the inbound stream
        ack_expected = 0;
        // Sequence number for the next outgoing frame
        next_frame_to_send = 0;
        // Expected frame's sequence number from the inbound stream
        frame_expected = 0;
        // Limiting the frame that can be received by the receiver's window
        too_far = NR_BUFS;
        // Initializing arrived[]
        for (i = 0; i < NR_BUFS; i++) arrived[i] = false;
        // Initializing the protocol
		init();
        // Enable network layer to send NR_BUFS number of frame
        enable_network_layer(NR_BUFS);

        // Infinite loop
        while (true) {	

            // Waiting for an event
            wait_for_event(event);

            // Do something based on the event type
            switch (event.type) {

                // When the network layer is ready for transmission
                case (PEvent.NETWORK_LAYER_READY):
                    // Get data from the network layer
                    from_network_layer(out_buf[next_frame_to_send % NR_BUFS]);

                    // Send the data
                    send_frame(PFrame.DATA, next_frame_to_send, frame_expected, out_buf);

                    // Slide the sender's window by one
                    next_frame_to_send = (next_frame_to_send + 1) % (MAX_SEQ + 1);
                break; 

                // When there is an arriving frame
                case (PEvent.FRAME_ARRIVAL ):

                    // Fetch the frame from the physical layer
                    from_physical_layer(r);

                    if (r.kind == PFrame.DATA) {
                        // Send a NAK if the frame is not the one expected by the receiver's window
                        if ((r.seq != frame_expected) && no_nak)
                            send_frame(PFrame.NAK, 0, frame_expected, out_buf);

                        // Start the acknowledgment timer, in case there is no outgoing frame that can be piggybacked
                        else
                            start_ack_timer(); 

                        // Store the incoming frame in the buffer if the sequence number is between the receiver's window
                        if (between(frame_expected, r.seq, too_far) && (arrived[r.seq % NR_BUFS] == false)) {
                            // Mark the buffer as occupied
                            arrived[r.seq % NR_BUFS] = true;

                            // Store the incoming frame in the incoming buffer
                            in_buf[r.seq % NR_BUFS] = r.info;

                            // Sending to the network layer whenever the LOWER_BOUND has been received, up to the next NOT RECEIVED FRAME
                            while (arrived[frame_expected % NR_BUFS]) {
                                // Passing the frame to the network layer
                                to_network_layer(in_buf[frame_expected % NR_BUFS]);

                                // Allow the protocol to receive NAK
                                no_nak = true;

                                // Mark the buffer as unoccupied
                                arrived[frame_expected % NR_BUFS] = false;

                                // Slide the receiver's window by one
                                frame_expected = (frame_expected + 1) % (MAX_SEQ + 1);

                                // Slide the receiver's window by one
                                too_far = (too_far + 1) % (MAX_SEQ + 1);

                                // Start the acknowledgment timer, in case there is no outgoing frame that can be piggybacked
                                start_ack_timer();
                            }
                        }
                    }

                    // If the received frame is a NAK
                    if ((r.kind == PFrame.NAK) && between(ack_expected, (r.ack + 1) % (MAX_SEQ + 1), next_frame_to_send))
                        // Resend the frame since the frame is lost
                        send_frame(PFrame.DATA, (r.ack + 1) % (MAX_SEQ + 1), frame_expected, out_buf);

					// Basically, when we receive r.ack, all ack between that ack_expected and r.ack
					// is assumed to be received too and hence, we can grant credit for the next packet transmission
                    while (between(ack_expected, r.ack, next_frame_to_send)) {
                        // Stop the timer since the acknowledgment has been received
                        stop_timer(ack_expected);

                        // Advancing the lower edge of the sender's window
                        ack_expected = (ack_expected + 1) % (MAX_SEQ + 1);

                        // Grant 1 credit to network layer to be able to send another frame
                        enable_network_layer(1);
                    }

                break;	   

                // In case the data is damaged
                case (PEvent.CKSUM_ERR):
                    // Send a NAK because the data is damaged
                    if (no_nak) send_frame(PFrame.NAK, 0, frame_expected, out_buf);
                break;  

                // In case there is a frame timeout
                case (PEvent.TIMEOUT): 
                    // Resend the data since the sender has not received any acknowledgment for it
                    send_frame(PFrame.DATA, oldest_frame, frame_expected, out_buf);
                break; 

                // In case there is no outgoing frame that can be piggybacked
                case (PEvent.ACK_TIMEOUT): 
                    // Send a separate acknowledgment frame
                    send_frame(PFrame.ACK, 0, frame_expected, out_buf);
                break; 

                default: 
                    System.out.println("SWP: undefined event type = " + event.type); 
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


