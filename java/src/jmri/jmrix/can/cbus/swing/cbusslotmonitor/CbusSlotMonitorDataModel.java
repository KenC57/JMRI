package jmri.jmrix.can.cbus.swing.cbusslotmonitor;

import java.util.ArrayList;
import java.util.TimerTask;

import javax.swing.JButton;

import jmri.*;
import jmri.jmrit.catalog.NamedIcon;
import jmri.jmrix.can.CanListener;
import jmri.jmrix.can.CanMessage;
import jmri.jmrix.can.CanReply;
import jmri.jmrix.can.CanSystemConnectionMemo;
import jmri.jmrix.can.cbus.CbusConstants;
import jmri.jmrix.can.cbus.CbusMessage;
import jmri.jmrix.can.TrafficController;
import jmri.util.swing.TextAreaFIFO;
import jmri.util.ThreadingUtil;
import jmri.util.TimerUtil;

/**
 * Table data model for display of CBUS Command Station Sessions and various Tools
 *
 * @author Steve Young (c) 2018 2019
 * @see CbusSlotMonitorPane
 *
 */
public class CbusSlotMonitorDataModel extends javax.swing.table.AbstractTableModel implements CanListener, Disposable  {

    private final TextAreaFIFO tablefeedback;
    private final TrafficController tc;
    private final CanSystemConnectionMemo memo;
    private final ArrayList<CbusSlotMonitorSession> _mainArray;

    protected int _contype=0; //  pane console message type
    protected String _context; // pane console text
    private int cmndstat_fw =0; // command station firmware  TODO - get from node table

    public static int CS_TIMEOUT = 2000; // command station timeout for estop and track messages
    private static final int MAX_LINES = 5000;

    // column order needs to match list in column tooltips
    public static final int SESSION_ID_COLUMN = 0;
    public static final int LOCO_ID_COLUMN = 1;
    public static final int ESTOP_COLUMN = 2;
    public static final int LOCO_ID_LONG_COLUMN = 3;
    public static final int LOCO_COMMANDED_SPEED_COLUMN = 4;
    public static final int LOCO_DIRECTION_COLUMN = 5;
    public static final int FUNCTION_LIST = 6;
    public static final int SPEED_STEP_COLUMN = 7;
    public static final int LOCO_CONSIST_COLUMN = 8;
    public static final int FLAGS_COLUMN = 9;
    public static final int KILL_SESSION_COLUMN = 10;
    public static final int LAUNCH_THROTTLE = 11;

    public static final int MAX_COLUMN = 12;

    static final int[] CBUSSLOTMONINITIALCOLS = {0,1,2,4,5,6,9,10,11};

    /**
     * Create a New CbusSlotMonitorDataModel.
     * Public access for user scripting.
     * @param memo CAN System Connection to monitor.
     */
    public CbusSlotMonitorDataModel(CanSystemConnectionMemo memo) {

        _mainArray = new ArrayList<>(0);
        tablefeedback = new TextAreaFIFO(MAX_LINES);
        tablefeedback.setEditable ( false );

        // connect to the CanInterface
        tc = memo.getTrafficController();
        addTc(tc);
        this.memo = memo;
        log.debug("Starting {} CbusSlotMonitorDataModel", memo.getUserName());

    }

    protected TextAreaFIFO tablefeedback(){
        return tablefeedback;
    }

    // order needs to match column list top of tabledatamodel
    static final String[] CBUSSLOTMONTOOLTIPS = {
        ("Session ID"),
        null, // loco id
        null, // estop
        ("If Loco ID heard by long address format"),
        ("Speed Commanded by throttle / CAB"),
        ("Forward or Reverse"),
        ("Any Functions set to ON"),
        ("Speed Steps"),
        null, // consist id
        null, // flags
        Bundle.getMessage("ReleaseTip"),  // send KLOC
        Bundle.getMessage("LaunchThrottleTip")

    }; // Length = number of items in array should (at least) match number of columns

    /**
     * Return the number of rows to be displayed.
     */
    @Override
    public int getRowCount() {
        return _mainArray.size();
    }

    @Override
    public int getColumnCount() {
        return MAX_COLUMN;
    }

    /**
     * Returns String of column name from column int
     * used in table header
     * @param col int col number
     */
    @Override
    public String getColumnName(int col) {
        switch (col) {
            case SESSION_ID_COLUMN:
                return Bundle.getMessage("OPC_SN"); // Session
            case LOCO_ID_COLUMN:
                return Bundle.getMessage("LocoID"); // Loco ID
            case LOCO_ID_LONG_COLUMN:
                return Bundle.getMessage("Long"); // Long
            case LOCO_CONSIST_COLUMN:
                return Bundle.getMessage("OPC_CA"); // Consist ID
            case LOCO_DIRECTION_COLUMN:
                return Bundle.getMessage("TrafficDirection"); // Direction
            case LOCO_COMMANDED_SPEED_COLUMN:
                return Bundle.getMessage("Speed");
            case ESTOP_COLUMN:
                return Bundle.getMessage("EStop");
            case SPEED_STEP_COLUMN:
                return Bundle.getMessage("Steps");
            case FLAGS_COLUMN:
                return Bundle.getMessage("OPC_FL"); // Flags
            case FUNCTION_LIST:
                return Bundle.getMessage("Functions");
            case KILL_SESSION_COLUMN:
                return Bundle.getMessage("Release");
            case LAUNCH_THROTTLE:
                return Bundle.getMessage("ThrottleTitle");
            default:
                return "unknown"; // NOI18N
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Class<?> getColumnClass(int col) {
        switch (col) {
            case SESSION_ID_COLUMN:
            case LOCO_ID_COLUMN:
            case LOCO_CONSIST_COLUMN:
                return Integer.class;
            case LOCO_ID_LONG_COLUMN:
                return Boolean.class;
            case LOCO_DIRECTION_COLUMN:
            case FUNCTION_LIST:
            case FLAGS_COLUMN:
            case SPEED_STEP_COLUMN:
            case LOCO_COMMANDED_SPEED_COLUMN:
                return String.class;
            case ESTOP_COLUMN:
            case KILL_SESSION_COLUMN:
            case LAUNCH_THROTTLE:
                return JButton.class;
            default:
                return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCellEditable(int row, int col) {
        switch (col) {
            case ESTOP_COLUMN:
                return _mainArray.get(row).getSessionId() > 0;
            case LAUNCH_THROTTLE:
                return true;
            case KILL_SESSION_COLUMN:
                return isJmriManagedThrottle(_mainArray.get(row).getLocoAddr());
            default:
                return false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getValueAt(int row, int col) {
        switch (col) {
            case SESSION_ID_COLUMN:
                if (_mainArray.get(row).getSessionId() > 0) {
                    return _mainArray.get(row).getSessionId();
                } else {
                    return "";
                }
            case LOCO_ID_COLUMN:
                return _mainArray.get(row).getLocoAddr().getNumber();
            case LOCO_ID_LONG_COLUMN:
                return _mainArray.get(row).getLocoAddr().isLongAddress();
            case LOCO_CONSIST_COLUMN:
                return _mainArray.get(row).getConsistId();
            case FLAGS_COLUMN:
                return _mainArray.get(row).getFlagString();
            case LOCO_DIRECTION_COLUMN:
                return _mainArray.get(row).getDirection();
            case LOCO_COMMANDED_SPEED_COLUMN:
                return _mainArray.get(row).getCommandedSpeed();
            case ESTOP_COLUMN:
                if ( _mainArray.get(row).getSessionId() > 0 ) { // is active session
                    return new NamedIcon("resources/icons/throttles/estop.png", "resources/icons/throttles/estop.png");
                }
                return null; // disables button if action is not possible
            case FUNCTION_LIST:
                return _mainArray.get(row).getFunctionString();
            case SPEED_STEP_COLUMN:
                return _mainArray.get(row).getSpeedSteps();
            case KILL_SESSION_COLUMN:
                if ( isJmriManagedThrottle(_mainArray.get(row).getLocoAddr()) ) {
                    return Bundle.getMessage("Release");
                }
                return null; // disables button if action is not possible
            case LAUNCH_THROTTLE:
                return Bundle.getMessage("ThrottleTitle");
            default:
                log.error("internal state inconsistent with table request for row {} col {}", row, col);
                return null;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setValueAt(Object value, int row, int col) {
        // log.debug("427 set valueat called row: {} col: {}", row, col);
        switch (col) {
            case SESSION_ID_COLUMN:
                _mainArray.get(row).setSessionId( (Integer) value );
                updateGui(row,col);
                updateGui(row, KILL_SESSION_COLUMN);
                break;
            case LOCO_CONSIST_COLUMN:
                _mainArray.get(row).setConsistId( (Integer) value );
                updateGui(row,col);
                break;
            case LOCO_COMMANDED_SPEED_COLUMN:
                _mainArray.get(row).setDccSpeed( (Integer) value );
                updateGui(row,col);
                updateGui(row,LOCO_DIRECTION_COLUMN);
                updateMemory(_mainArray.get(row));
                break;
            case ESTOP_COLUMN:
                int stopspeed=1;
                if ( _mainArray.get(row).getDirection().equals(Bundle.getMessage("FWD") )
                    && _mainArray.get(row).getSpeedSteps().equals("128") ) {
                    stopspeed=129;
                }
                CanMessage m = new CanMessage(tc.getCanid());
                m.setNumDataElements(3);
                CbusMessage.setPri(m, CbusConstants.DEFAULT_DYNAMIC_PRIORITY * 4 + CbusConstants.DEFAULT_MINOR_PRIORITY);
                m.setElement(0, CbusConstants.CBUS_DSPD);
                m.setElement(1, _mainArray.get(row).getSessionId() );
                m.setElement(2, stopspeed);
                tc.sendCanMessage(m, null);
                break;
            case SPEED_STEP_COLUMN:
                _mainArray.get(row).setSpeedSteps( (String) value );
                updateGui(row,col);
                break;
            case KILL_SESSION_COLUMN:
                CanMessage msg = new CanMessage(2, tc.getCanid());
                CbusMessage.setPri(msg, CbusConstants.DEFAULT_DYNAMIC_PRIORITY * 4 + CbusConstants.DEFAULT_MINOR_PRIORITY);
                msg.setOpCode(CbusConstants.CBUS_KLOC);
                msg.setElement(1, _mainArray.get(row).getSessionId());
                tc.sendCanMessage(msg, null);
                break;
            case LAUNCH_THROTTLE:
                var tf = InstanceManager.getDefault(jmri.jmrit.throttle.ThrottleFrameManager.class).createThrottleFrame();
                tf.toFront();
                tf.getAddressPanel().setCurrentAddress(_mainArray.get(row).getLocoAddr() );
                break;
            default:
                log.warn("Failed to set value at column {}",col);
                break;
        }
    }

    private void updateGui(int row,int col) {
        ThreadingUtil.runOnGUI( ()-> fireTableCellUpdated(row, col));
    }

    private boolean maintainLocoSpdMemory = false;

    /**
     * Set true to maintain a Memory Variable for the speed of each loco.
     * Note this is an experimental method ( 5.5.5 ) and may be subject to change.
     * <p>
     * The Memory System Name is in the form e.g. IM12(S) or IM789(L)
     * i.e. Internal Memory Loco 12, Short address.
     * It may be easier to refer to this Memory in Jython scripts
     * by giving it a User Name.
     * <p>
     * The Memory Value is the commanded Loco speed, 0-126.
     * 0 includes a normal stop and e-stop.
     * <p>
     * The Value updates whenever a Loco speed command is heard on the
     * connection hence not restricted to this JMRI instance.
     * @since 5.5.5
     * @param newVal true to enable updates, false to stop updates.
     *               Default is false, no updates provided.
     */
    public void setMaintainLocoSpdMemory(boolean newVal) {
        maintainLocoSpdMemory = newVal;
    }

    private void updateMemory(CbusSlotMonitorSession session){
        if ( !maintainLocoSpdMemory || session==null ){
            return;
        }
        MemoryManager memMgr = InstanceManager.getDefault(MemoryManager.class);
        memMgr.provideMemory( memMgr.getSystemNamePrefix() + session.getLocoAddr() ).setValue(
            jmri.util.StringUtil.getFirstIntFromString(session.getCommandedSpeed()));
    }

    private int createnewrow(int locoid, Boolean islong){

        DccLocoAddress addr = new DccLocoAddress(locoid,islong );
        CbusSlotMonitorSession newSession = new CbusSlotMonitorSession(addr);

        _mainArray.add(newSession);
        fireTableRowsInserted((getRowCount()-1), (getRowCount()-1));
        return getRowCount()-1;
    }

    // returning the row number not the session
    // so that any updates go through the table model
    // and are updated in the GUI
    private int provideTableRow( DccLocoAddress addr ) {
        for (int i = 0; i < getRowCount(); i++) {
            if ( addr.equals(_mainArray.get(i).getLocoAddr() ) )  {
                return i;
            }
        }
        return createnewrow(addr.getNumber(),addr.isLongAddress());
    }

    private int getrowfromsession(int sessionid){
        for (int i = 0; i < getRowCount(); i++) {
            if (sessionid==_mainArray.get(i).getSessionId() )  {
                return i;
            }
        }
        // no row so request session details from command station
        CanMessage m = new CanMessage(tc.getCanid());
        m.setNumDataElements(2);
        CbusMessage.setPri(m, CbusConstants.DEFAULT_DYNAMIC_PRIORITY * 4 + CbusConstants.DEFAULT_MINOR_PRIORITY);
        m.setElement(0, CbusConstants.CBUS_QLOC);
        m.setElement(1, sessionid);
        tc.sendCanMessage(m, null);
        // should receive a PLOC response with loco id etc.
        return -1;
    }

    /**
     * @param m outgoing CanMessage
     */
    @Override
    public void message(CanMessage m) {
        if ( m.extendedOrRtr() ) {
            return;
        }
        int opc = CbusMessage.getOpcode(m);
        // process is false as outgoing
        switch (opc) {
            case CbusConstants.CBUS_PLOC:
                {
                    int rcvdIntAddr = (m.getElement(2) & 0x3f) * 256 + m.getElement(3);
                    boolean rcvdIsLong = (m.getElement(2) & 0xc0) != 0;
                    processploc(m.getElement(1),new DccLocoAddress(rcvdIntAddr,rcvdIsLong),m.getElement(4),
                            m.getElement(5),m.getElement(6),m.getElement(7));
                    break;
                }
            case CbusConstants.CBUS_RLOC:
                {
                    int rcvdIntAddr = (m.getElement(1) & 0x3f) * 256 + m.getElement(2);
                    boolean rcvdIsLong = (m.getElement(1) & 0xc0) != 0;
                    processrloc(false,new DccLocoAddress(rcvdIntAddr,rcvdIsLong));
                    break;
                }
            case CbusConstants.CBUS_DSPD:
                processdspd(m.getElement(1),m.getElement(2));
                break;
            case CbusConstants.CBUS_DKEEP:
                processdkeep(m.getElement(1));
                break;
            case CbusConstants.CBUS_KLOC:
                processkloc(false,m.getElement(1));
                break;
            case CbusConstants.CBUS_GLOC:
                int rcvdIntAddr = (m.getElement(1) & 0x3f) * 256 + m.getElement(2);
                boolean rcvdIsLong = (m.getElement(1) & 0xc0) != 0;
                processgloc(false,new DccLocoAddress(rcvdIntAddr,rcvdIsLong),m.getElement(3));
                break;
            case CbusConstants.CBUS_ERR:
                processerr(false,m.getElement(1),m.getElement(2),m.getElement(3));
                break;
            case CbusConstants.CBUS_STMOD:
                processstmod(false,m.getElement(1),m.getElement(2));
                break;
            case CbusConstants.CBUS_DFUN:
                processdfun(m.getElement(1),m.getElement(2),m.getElement(3));
                break;
            case CbusConstants.CBUS_DFNON:
                processdfnon(m.getElement(1),m.getElement(2),true);
                break;
            case CbusConstants.CBUS_DFNOF:
                processdfnon(m.getElement(1),m.getElement(2),false); // same routine as DFNON
                break;
            case CbusConstants.CBUS_PCON:
                processpcon(m.getElement(1),m.getElement(2));
                break;
            case CbusConstants.CBUS_KCON:
                processpcon(m.getElement(1),0); // same routine as PCON
                break;
            case CbusConstants.CBUS_DFLG:
                processdflg(m.getElement(1),m.getElement(2));
                break;
            case CbusConstants.CBUS_ESTOP:
                processestop();
                break;
            case CbusConstants.CBUS_RTON:
                processrton();
                break;
            case CbusConstants.CBUS_RTOF:
                processrtof();
                break;
            case CbusConstants.CBUS_TON:
                processton();
                break;
            case CbusConstants.CBUS_TOF:
                processtof();
                break;
            default:
                break;
        }
    }

    /**
     * @param m incoming cbus CanReply
     */
    @Override
    public void reply(CanReply m) {
        if ( m.extendedOrRtr() ) {
            return;
        }
        int opc = CbusMessage.getOpcode(m);
        int rcvdIntAddr;
        boolean rcvdIsLong;
        DccLocoAddress addr;
        switch (opc) {
            case CbusConstants.CBUS_STAT:
                // todo more on this when finished tested v3 firmware with all opcs
                // for now, if a stat opc is received then it's v4
                // no stat received when < v4 Firmware
                cmndstat_fw = 4;
                break;
            case CbusConstants.CBUS_PLOC:
                rcvdIntAddr = (m.getElement(2) & 0x3f) * 256 + m.getElement(3);
                rcvdIsLong = (m.getElement(2) & 0xc0) != 0;
                addr = new DccLocoAddress(rcvdIntAddr,rcvdIsLong);
                processploc(m.getElement(1),addr,m.getElement(4),
                        m.getElement(5),m.getElement(6),m.getElement(7));
                break;
            case CbusConstants.CBUS_RLOC:
                rcvdIntAddr = (m.getElement(1) & 0x3f) * 256 + m.getElement(2);
                rcvdIsLong = (m.getElement(1) & 0xc0) != 0;
                addr = new DccLocoAddress(rcvdIntAddr,rcvdIsLong);
                processrloc(true,addr);
                break;
            case CbusConstants.CBUS_DSPD:
                processdspd(m.getElement(1),m.getElement(2));
                break;
            case CbusConstants.CBUS_DKEEP:
                processdkeep(m.getElement(1));
                break;
            case CbusConstants.CBUS_KLOC:
                processkloc(true,m.getElement(1));
                break;
            case CbusConstants.CBUS_GLOC:
                rcvdIntAddr = (m.getElement(1) & 0x3f) * 256 + m.getElement(2);
                rcvdIsLong = (m.getElement(1) & 0xc0) != 0;
                addr = new DccLocoAddress(rcvdIntAddr,rcvdIsLong);
                processgloc(true,addr,m.getElement(3));
                break;
            case CbusConstants.CBUS_ERR:
                processerr(true,m.getElement(1),m.getElement(2),m.getElement(3));
                break;
            case CbusConstants.CBUS_STMOD:
                processstmod(true,m.getElement(1),m.getElement(2));
                break;
            case CbusConstants.CBUS_DFUN:
                processdfun(m.getElement(1),m.getElement(2),m.getElement(3));
                break;
            case CbusConstants.CBUS_DFNON:
                processdfnon(m.getElement(1),m.getElement(2),true);
                break;
            case CbusConstants.CBUS_DFNOF:
                processdfnon(m.getElement(1),m.getElement(2),false);  // same routine as DFNON
                break;
            case CbusConstants.CBUS_PCON:
                processpcon(m.getElement(1),m.getElement(2));
                break;
            case CbusConstants.CBUS_KCON:
                processpcon(m.getElement(1),0); // same routine as PCON
                break;
            case CbusConstants.CBUS_DFLG:
                processdflg(m.getElement(1),m.getElement(2));
                break;
            case CbusConstants.CBUS_ESTOP:
                processestop();
                break;
            case CbusConstants.CBUS_RTON:
                processrton();
                break;
            case CbusConstants.CBUS_RTOF:
                processrtof();
                break;
            case CbusConstants.CBUS_TON:
                processton();
                break;
            case CbusConstants.CBUS_TOF:
                processtof();
                break;
            default:
                break;
        }
    }

    // ploc sent from a command station to a throttle
    private void processploc( int session, DccLocoAddress addr,
        int speeddir, int fa, int fb, int fc) {

        int row = provideTableRow(addr);
        setValueAt(session, row, SESSION_ID_COLUMN);
        setValueAt(speeddir, row, LOCO_COMMANDED_SPEED_COLUMN);
        processdfun( session, 1, fa);
        processdfun( session, 2, fb);
        processdfun( session, 3, fc);
    }

    // kloc sent from throttle to command station to release loco, which will continue at current speed
    private void processkloc(boolean messagein, int session) {
        int row=getrowfromsession(session);
        String messagedir;
        if (messagein){ // external throttle
            messagedir = Bundle.getMessage("CBUS_IN_CAB");
        } else { // jmri throttle
            messagedir = Bundle.getMessage("CBUS_OUT_CMD");
        }
        log.debug("direction {} kloc {}",messagedir,Bundle.getMessage("CNFO_KLOC",session));
        if ( row > -1 ) {
            setValueAt(0, row, SESSION_ID_COLUMN); // Session restored by sending QLOC if v4 firmware

            // version 4 fw maintains version number, so to check this request session details from command station
            // if this is sent with the v3 firmware then a popup error comes up from cbus throttlemanager when
            // errStr is populated in the switch error clauses in canreply.
            // check if version 4
            if ( ( cmndstat_fw > 3 ) && ( !"0".startsWith(_mainArray.get(row).getCommandedSpeed()) )) {
                log.debug("send qloc {} {}",Bundle.getMessage("CBUS_OUT_CMD"),Bundle.getMessage("QuerySession8a",session));
                CanMessage m = new CanMessage(tc.getCanid());
                m.setNumDataElements(2);
                CbusMessage.setPri(m, CbusConstants.DEFAULT_DYNAMIC_PRIORITY * 4 + CbusConstants.DEFAULT_MINOR_PRIORITY);
                m.setElement(0, CbusConstants.CBUS_QLOC);
                m.setElement(1, session);
                tc.sendCanMessage(m, null);
            }
        }
    }

    // rloc sent from throttle to command station to get loco
    private void processrloc(boolean messagein, DccLocoAddress addr ) {
        int row = provideTableRow(addr);
        log.debug("{} new table row {}", messagein,row);
    }

    // gloc sent from throttle to command station to get loco
    private void processgloc(boolean messagein, DccLocoAddress addr, int flags) {
        int row = provideTableRow(addr);
        log.debug ("processgloc row {}",row);
        StringBuilder flagstring = new StringBuilder();
        if (messagein){ // external throttle
            flagstring.append(Bundle.getMessage("CBUS_IN_CAB"));
        } else { // jmri throttle
            flagstring.append(Bundle.getMessage("CBUS_OUT_CMD"));
        }

        boolean stealmode = ((flags ) & 1) != 0;
        boolean sharemode = ((flags >> 1 ) & 1) != 0;
        if (stealmode){
            flagstring.append(Bundle.getMessage("CNFO_GLOC_ST"));
        }
        else if (sharemode){
            flagstring.append(Bundle.getMessage("CNFO_GLOC_SH"));
        }
        else {
            flagstring.append(Bundle.getMessage("CNFO_GLOC"));
        }
        flagstring.append(addr);
        addToLog(1,flagstring.toString());
    }

    // stmod sent from throttle to cmmnd station if speed steps not 128 / set service mode / sound mode
    private void processstmod(boolean messagein, int session, int flags) {
        int row=getrowfromsession(session);
        if ( row > -1 ) {
            String messagedir;
            if (messagein){ // external throttle
                messagedir=( Bundle.getMessage("CBUS_IN_CAB"));
            } else { // jmri throttle
                messagedir=( Bundle.getMessage("CBUS_OUT_CMD"));
            }

            boolean sm0 = ((flags ) & 1) != 0;
            boolean sm1 = ((flags >> 1 ) & 1) != 0;
            boolean servicemode = ((flags >> 2 ) & 1) != 0;
            boolean soundmode = ((flags >> 3 ) & 1) != 0;

            String speedstep="";
            if ((!sm0) && (!sm1)){
                speedstep="128";
            }
            else if ((!sm0) && (sm1)){
                speedstep="14";
            }
            else if ((sm0) && (!sm1)){
                speedstep="28I";
            }
            else if ((sm0) && (sm1)){
                speedstep="28";
            }
            log.debug("processstmod {} {}",messagedir,Bundle.getMessage("CNFO_STMOD",session,speedstep,servicemode,soundmode));
            setValueAt(speedstep, row, SPEED_STEP_COLUMN);
        }
    }

    // DKEEP sent as keepalive from throttle to command station
    private void processdkeep(int session) {
        int row=getrowfromsession(session);
        if ( row < 0 ) {
            log.debug("Requesting loco details for session {}.",session );
        }
    }

    // DSPD sent from throttle to command station , speed / direction
    private void processdspd( int session, int speeddir) {
        int row=getrowfromsession(session);
        if ( row > -1 ) {
            setValueAt(speeddir, row, LOCO_COMMANDED_SPEED_COLUMN);
        }
    }

    // DFLG sent from throttle to command station to notify engine change in flags
    private void processdflg( int session, int flags) {
        int row=getrowfromsession(session);
        if ( row>-1 ) {
            _mainArray.get(row).setFlags(flags);
            updateGui(row,SPEED_STEP_COLUMN);
            updateGui(row,FLAGS_COLUMN);
        }
    }

    // DFNON Sent by a cab to turn on a specific loco function, alternative method to DFUN
    // also used to process function responses from DFNOF
    private void processdfnon( int session, int function, boolean trueorfalse) {
        int row=getrowfromsession(session);
        if ( row>-1 && function>-1 && function<29 ) {
            _mainArray.get(row).setFunction(function,trueorfalse);
            updateGui(row,FUNCTION_LIST);
        }
    }

    // DFUN Sent by a cab to trigger loco function
    // also used to process function responses from PLOC
    private void processdfun( int session, int range, int functionbyte) {
        int row=getrowfromsession(session);
        if ( row > -1 ) {
            switch (range) {
                case 1:
                    _mainArray.get(row).setFunction(0, ((functionbyte & CbusConstants.CBUS_F0) == CbusConstants.CBUS_F0));
                    _mainArray.get(row).setFunction(1, ((functionbyte & CbusConstants.CBUS_F1) == CbusConstants.CBUS_F1));
                    _mainArray.get(row).setFunction(2, ((functionbyte & CbusConstants.CBUS_F2) == CbusConstants.CBUS_F2));
                    _mainArray.get(row).setFunction(3, ((functionbyte & CbusConstants.CBUS_F3) == CbusConstants.CBUS_F3));
                    _mainArray.get(row).setFunction(4, ((functionbyte & CbusConstants.CBUS_F4) == CbusConstants.CBUS_F4));
                    break;
                case 2:
                    _mainArray.get(row).setFunction(5, ((functionbyte & CbusConstants.CBUS_F5) == CbusConstants.CBUS_F5));
                    _mainArray.get(row).setFunction(6, ((functionbyte & CbusConstants.CBUS_F6) == CbusConstants.CBUS_F6));
                    _mainArray.get(row).setFunction(7, ((functionbyte & CbusConstants.CBUS_F7) == CbusConstants.CBUS_F7));
                    _mainArray.get(row).setFunction(8, ((functionbyte & CbusConstants.CBUS_F8) == CbusConstants.CBUS_F8));
                    break;
                case 3:
                    _mainArray.get(row).setFunction(9, ((functionbyte & CbusConstants.CBUS_F9) == CbusConstants.CBUS_F9));
                    _mainArray.get(row).setFunction(10, ((functionbyte & CbusConstants.CBUS_F10) == CbusConstants.CBUS_F10));
                    _mainArray.get(row).setFunction(11, ((functionbyte & CbusConstants.CBUS_F11) == CbusConstants.CBUS_F11));
                    _mainArray.get(row).setFunction(12, ((functionbyte & CbusConstants.CBUS_F12) == CbusConstants.CBUS_F12));
                    break;
                case 4:
                    _mainArray.get(row).setFunction(13, ((functionbyte & CbusConstants.CBUS_F13) == CbusConstants.CBUS_F13));
                    _mainArray.get(row).setFunction(14, ((functionbyte & CbusConstants.CBUS_F14) == CbusConstants.CBUS_F14));
                    _mainArray.get(row).setFunction(15, ((functionbyte & CbusConstants.CBUS_F15) == CbusConstants.CBUS_F15));
                    _mainArray.get(row).setFunction(16, ((functionbyte & CbusConstants.CBUS_F16) == CbusConstants.CBUS_F16));
                    _mainArray.get(row).setFunction(17, ((functionbyte & CbusConstants.CBUS_F17) == CbusConstants.CBUS_F17));
                    _mainArray.get(row).setFunction(18, ((functionbyte & CbusConstants.CBUS_F18) == CbusConstants.CBUS_F18));
                    _mainArray.get(row).setFunction(19, ((functionbyte & CbusConstants.CBUS_F19) == CbusConstants.CBUS_F19));
                    _mainArray.get(row).setFunction(20, ((functionbyte & CbusConstants.CBUS_F20) == CbusConstants.CBUS_F20));
                    break;
                case 5:
                    _mainArray.get(row).setFunction(21, ((functionbyte & CbusConstants.CBUS_F21) == CbusConstants.CBUS_F21));
                    _mainArray.get(row).setFunction(22, ((functionbyte & CbusConstants.CBUS_F22) == CbusConstants.CBUS_F22));
                    _mainArray.get(row).setFunction(23, ((functionbyte & CbusConstants.CBUS_F23) == CbusConstants.CBUS_F23));
                    _mainArray.get(row).setFunction(24, ((functionbyte & CbusConstants.CBUS_F24) == CbusConstants.CBUS_F24));
                    _mainArray.get(row).setFunction(25, ((functionbyte & CbusConstants.CBUS_F25) == CbusConstants.CBUS_F25));
                    _mainArray.get(row).setFunction(26, ((functionbyte & CbusConstants.CBUS_F26) == CbusConstants.CBUS_F26));
                    _mainArray.get(row).setFunction(27, ((functionbyte & CbusConstants.CBUS_F27) == CbusConstants.CBUS_F27));
                    _mainArray.get(row).setFunction(28, ((functionbyte & CbusConstants.CBUS_F28) == CbusConstants.CBUS_F28));
                    break;
                default:
                    break;
            }
            updateGui(row,FUNCTION_LIST);
        }
    }

    // ERR sent by command station
    private void processerr(boolean messagein, int one, int two, int errnum) {
        int rcvdIntAddr = (one & 0x3f) * 256 + two;

        StringBuilder buf = new StringBuilder();
        if (messagein){ // external throttle
            buf.append( Bundle.getMessage("CBUS_CMND_BR"));
        } else { // jmri throttle
            buf.append( Bundle.getMessage("CBUS_OUT_CMD"));
        }

        switch (errnum) {
            case 1:
                buf.append(Bundle.getMessage("ERR_LOCO_STACK_FULL"));
                buf.append(rcvdIntAddr);
                break;
            case 2:
                buf.append(Bundle.getMessage("ERR_LOCO_ADDRESS_TAKEN",rcvdIntAddr));
                break;
            case 3:
                buf.append(Bundle.getMessage("ERR_SESSION_NOT_PRESENT",one));
                break;
            case 4:
                buf.append(Bundle.getMessage("ERR_CONSIST_EMPTY"));
                buf.append(one);
                break;
            case 5:
                buf.append(Bundle.getMessage("ERR_LOCO_NOT_FOUND"));
                buf.append(one);
                break;
            case 6:
                buf.append(Bundle.getMessage("ERR_CAN_BUS_ERROR"));
                break;
            case 7:
                buf.append(Bundle.getMessage("ERR_INVALID_REQUEST"));
                buf.append(rcvdIntAddr);
                break;
            case 8:
                buf.append(Bundle.getMessage("ERR_SESSION_CANCELLED",one));
                // cancel session number in table
                int row = getrowfromsession(one);
                if ( row > -1 ) {
                    setValueAt(0, row, SESSION_ID_COLUMN);
                }
                break;
            default:
                break;
        }
        _context = buf.toString();
        addToLog(1,_context);
    }

    // PCON sent by throttle to add to consist
    // also used to process remove from consist KCON
    private void processpcon( int session, int consist){
        log.debug("processing pcon");
        int row=getrowfromsession(session);
        if ( row>-1 ) {

            int consistaddr = (consist & 0x7f);
            setValueAt(consistaddr, row, LOCO_CONSIST_COLUMN);

            StringBuilder buf = new StringBuilder();
            buf.append( Bundle.getMessage("CNFO_PCON",session,consistaddr));
            if ((consist & 0x80) == 0x80){
                buf.append( Bundle.getMessage("FWD"));
            } else {
                buf.append( Bundle.getMessage("REV"));
            }
            addToLog(1,buf.toString() );
        }
    }

    private void processestop(){
        addToLog(1,"Command station acknowledges estop");
        clearEStopTask();
    }

    private void processrton(){
        setPowerTask();
    }

    private void processrtof(){
        setPowerTask();
    }

    private void processton(){
        clearPowerTask();
        log.debug("Track on confirmed from command station.");
    }

    private void processtof(){
        clearPowerTask();
        log.debug("Track off confirmed from command station.");
    }

    public void sendcbusestop(){
        log.info("Sending Command Station e-stop");
        CanMessage m = new CanMessage(tc.getCanid());
        m.setNumDataElements(1);
        CbusMessage.setPri(m, CbusConstants.DEFAULT_DYNAMIC_PRIORITY * 4 + CbusConstants.DEFAULT_MINOR_PRIORITY);
        m.setElement(0, CbusConstants.CBUS_RESTP);
        tc.sendCanMessage(m, null);

        // start a timer to monitor if timeout, ie if command station doesn't respond
        setEstopTask();
    }

    private transient TimerTask eStopTask;

    private void clearEStopTask() {
        if (eStopTask != null ) {
            eStopTask.cancel();
            eStopTask = null;
        }
    }

    private void setEstopTask() {
        eStopTask = new TimerTask() {
            @Override
            public void run() {
                eStopTask = null;
                addToLog(1,("Send Estop No Response received from command station."));
                log.info("Send Estop No Response received from command station.");
            }
        };
        TimerUtil.schedule(eStopTask, ( CS_TIMEOUT ) );
    }

    private transient TimerTask powerTask;

    private void clearPowerTask() {
        if (powerTask != null ) {
            powerTask.cancel();
            powerTask = null;
        }
    }

    private void setPowerTask() {
        powerTask = new TimerTask() {
            @Override
            public void run() {
                powerTask = null;
                addToLog(1,("Track Power - No Response received from command station."));
                log.info("Track Power - No Response received from command station.");
            }
        };
        TimerUtil.schedule(powerTask, ( CS_TIMEOUT ) );
    }

    /**
     * Add to Slot Monitor Console Log
     * @param cbuserror int
     * @param cbustext String console message
     */
    public void addToLog(int cbuserror, String cbustext){
        ThreadingUtil.runOnGUI( ()-> tablefeedback.append( System.lineSeparator()+cbustext));
    }

    private boolean isJmriManagedThrottle(LocoAddress addr) {
        ThrottleManager tm = memo.getFromMap(ThrottleManager.class);
        return tm != null && tm.getThrottleUsageCount(addr) > 0;
    }

    /**
     * disconnect from the CBUS
     */
    @Override
    public void dispose() {
        removeTc(tc);

        // stop timers if running
        clearEStopTask();
        clearPowerTask();

        tablefeedback.dispose();

    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CbusSlotMonitorDataModel.class);

}
