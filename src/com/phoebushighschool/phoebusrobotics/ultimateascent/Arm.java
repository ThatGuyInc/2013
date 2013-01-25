package com.PhoebusHighSchool.PhoebusRobotics.UltimateAscent;

import edu.wpi.first.wpilibj.CANJaguar;
import edu.wpi.first.wpilibj.DigitalInput;
import edu.wpi.first.wpilibj.can.CANTimeoutException;

public class Arm {

    protected boolean catchState = false;
    protected CANJaguar motor;
//    protected DigitalInput latchSwitch;
//    protected DigitalInput handOffSwitch;

    public Arm() {
//        latchSwitch = new DigitalInput(1,
//                Parameters.latchLimitSwitchGPIOChannel);
//        handOffSwitch = new DigitalInput(1
//                , Parameters.handOffLimitSwitchGPIOChannel);
        try {
            motor = new CANJaguar(Parameters.ArmMovementSomething);
            motor.configMaxOutputVoltage(Parameters.MaxMotorOutputVoltage);
            motor.changeControlMode(CANJaguar.ControlMode.kPercentVbus);
        } catch (CANTimeoutException ex) {
            ex.printStackTrace();
        }
    }

    public boolean extend() throws CANTimeoutException {
        motor.setX(12.0);
        if (!motor.getForwardLimitOK()) {
            motor.setX(0.0);
            System.out.println("All the way out!");
            return true;
        }
        return false;
    }

    public void retract() throws CANTimeoutException {
        if (!isFullyRetracted()) {
            motor.setX(-12.0);
        } else {
            motor.setX(0.0);
        }
    }
//       too narrow
//    public boolean isLatched() {
//        if (latchSwitch.get()) {
//            catchState = true;
//        }
//        return catchState;
//    }

    public boolean isLatched() {
        return false;
    }

    public boolean isFullyExtended() throws CANTimeoutException {
        return motor.getForwardLimitOK();
    }

    public boolean isFullyRetracted() throws CANTimeoutException {
        return motor.getReverseLimitOK();
    }

    public void stop() {
        try {
            motor.setX(0.0);
        } catch (CANTimeoutException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * this inner class is used as a parameter to specify the direction the arm
     * should move in.
     */
    public class Direction {

        private final int value;
        private static final int kForwardValue = 1;
        private static final int kReverseValue = 2;
        public static final Direction kForward = new Direction(kForwardValue);
        public static final Direction kReverse = new Direction(kReverseValue);

        public Direction(int direction) {
            this.value = direction;

        }
    }
}
