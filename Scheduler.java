
/**
 * The Implementation of the Scheduler Class
 */

import java.io.*;
import java.net.*;
import java.util.ArrayList;

public class Scheduler {

	// Packets and Sockets
	DatagramPacket floorSendPacket, elevatorSendPacket, receivePacket;
	DatagramSocket sendSocket, receiveSocket;

	//Queue for received packets
	ArrayList<DatagramPacket> receiveQueue;

	// Lamps and Sensors
	private boolean floorLamps[];
	private ArrayList<Integer> reqFloors;
	private boolean arrivalSensors[];

	// Total number of floors
	private final int numFloors;

	// Elevator Data List
	private ElevatorData elevData[];

	// Elevator Routing
	private ArrayList<ElevatorData> potentialRoutes;
	private int routedElevator;

	// Data Structures for relaying Data
	private SchedulerData scheDat;
	private FloorData floorDat;
	private ElevatorData elevDat;

	private ArrayList<FloorData> pendRequests;

	/**
	 * Create a new Scheduler with the corresponding number of floors
	 *
	 * @param numFloors
	 */
	public Scheduler(int numFloors, int numElevators) {
		try {
			// Construct a datagram socket and bind it to any available
			// port on the local host machine. This socket will be used to
			// send UDP Datagram packets.
			sendSocket = new DatagramSocket();

			// Construct a datagram socket and bind it to port 3000
			// on the local host machine. This socket will be used to
			// receive UDP Datagram packets.

			receiveSocket = new DatagramSocket(3000);

		} catch (SocketException se) {
			se.printStackTrace();
			System.exit(1);
		}

		receiveQueue = new ArrayList<DatagramPacket>();

		this.numFloors = numFloors;
		floorLamps = new boolean[numFloors];
		arrivalSensors = new boolean[numFloors];
		reqFloors = new ArrayList<Integer>();
		pendRequests = new ArrayList<FloorData>(); 

		elevData = new ElevatorData[numElevators];
		for (int i = 0; i < numElevators; i++) {
			// Assume same starting position as set in elevator subsystem
			elevData[i] = new ElevatorData(i, 2000 + i, ElevatorData.NO_ERROR, 
					1, new ArrayList<Integer>(), false, false, false, false);
		}

	}

	/**
	 * Close the sockets
	 */
	public void closeSockets() {
		// We're finished, so close the sockets.
		sendSocket.close();
		receiveSocket.close();
	}

	/**
	 * Send the Floor subsystem a data packet
	 *
	 * @param scheDat
	 *            the scheduler data
	 */
	public void floorSend(SchedulerData scheDat) {

		this.scheDat = scheDat;

		try {
			// Convert the FloorData object into a byte array
			ByteArrayOutputStream baoStream = new ByteArrayOutputStream();
			ObjectOutputStream ooStream;
			ooStream = new ObjectOutputStream(new BufferedOutputStream(baoStream));
			ooStream.flush();
			ooStream.writeObject(scheDat);
			ooStream.flush();
			byte msg[] = baoStream.toByteArray();

			floorSendPacket = new DatagramPacket(msg, msg.length, receivePacket.getAddress(),
					receivePacket.getPort());
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		// Send the datagram packet to the client via the send socket.
		try {
			sendSocket.send(floorSendPacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		print("Scheduler: Sent packet to FloorSubsystem.");

	}

	/**
	 * Send the Elevator subsystem a data packet
	 *
	 * @param scheDat
	 *            the scheduler data
	 */
	public void elevatorSend(SchedulerData scheDat) {

		this.scheDat = scheDat;
		int targetPort = 2000 + scheDat.getElevatorNumber();
		try {
			// Convert the FloorData object into a byte array
			ByteArrayOutputStream baoStream = new ByteArrayOutputStream();
			ObjectOutputStream ooStream;
			ooStream = new ObjectOutputStream(new BufferedOutputStream(baoStream));
			ooStream.flush();
			ooStream.writeObject(scheDat);
			ooStream.flush();
			byte msg[] = baoStream.toByteArray();

			elevatorSendPacket = new DatagramPacket(msg, msg.length, receivePacket.getAddress(), targetPort);


		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		// Send the datagram packet to the client via the send socket.
		try {
			sendSocket.send(elevatorSendPacket);
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}

		print("Scheduler: Sent packet to ElevatorSubsystem.");
	}

	/**
	 * Receive a packet
	 */
	public void receive() {
		byte data[] = new byte[5000];
		receivePacket = new DatagramPacket(data, data.length);
		print("Scheduler: Waiting for Packet.\n");

		// Block until a datagram packet is received from receiveSocket.
		try {
			// print("Waiting..."); // so we know we're waiting
			receiveSocket.receive(receivePacket);
			receiveQueue.add(receivePacket);
		} catch (IOException e) {
			print("IO Exception: likely:");
			print("Receive Socket Timed Out.\n" + e);
			e.printStackTrace();
			System.exit(1);
		}
	}

	public void processAndSend() {

		try {
			// Process all received packets and retrieve the FloorData, ElevatorData objects
			if (!receiveQueue.isEmpty()) {
				for (DatagramPacket dPacket : receiveQueue) {
					//Convert packet's byte array into the object
					ByteArrayInputStream byteStream = new ByteArrayInputStream(dPacket.getData());
					ObjectInputStream is;
					is = new ObjectInputStream(new BufferedInputStream(byteStream));
					Object o = is.readObject();
					is.close();

					if (o instanceof FloorData) {
						floorDat = (FloorData) o;
						print("Scheduler: Packet received.");
						print("Containing:\n	" + floorDat.getStatus() + "\n");

						updateRequests();
						routeElevator();
						clearRequest();
					} else {
						elevDat = (ElevatorData) o;
						print("Scheduler: Packet received.");
						print("Containing:\n	" + elevDat.getStatus() + "\n");

						elevData[elevDat.getElevatorNumber()] = elevDat;
						displayElevatorStates();
						manageElevators();
					}
				}
			}

			receiveQueue.clear();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}


	}

	/**
	 * Wait for the specified amount of time
	 */
	public void wait(int ms) {
		// Slow things down (wait 5 seconds)
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Update the scheduler's floor requests
	 */
	public void updateRequests() {
		if (!pendRequests.contains(floorDat))
			pendRequests.add(floorDat);
	}

	/**
	 * Display all elevator statuses 
	 */
	public void displayElevatorStates() {
		print("ELEVATOR STATUS:");
		for (ElevatorData e : elevData) {
			print("	" + e.getStatus());
		}
		print("\n");
	}

	/**
	 * Manage the elevator that last updated its status
	 */
	public void manageElevators() {
		ElevatorData e = elevDat;
		SchedulerData s = null;
		int errType = e.getErrorType();
		int currentFloor = e.getCurrentFloor();

		switch(errType) {
		case ElevatorData.NO_ERROR:
			// If elevator is on the current requested floor
			if (e.getRequestedFloors().contains(currentFloor)) {
				//If motor is still active, stop and open doors
				print("SIGNAL STOP to Elevator: " + e.getElevatorNumber() + ".");
				s = new SchedulerData(e.getElevatorNumber(), SchedulerData.STOP_REQUEST, false, false, true);

			}

			//If elevator has not reached it's current destination
			else if (!e.getRequestedFloors().isEmpty()) {
				// If elevator is above floor, move down, close doors
				if (currentFloor > e.getRequestedFloors().get(0) && e.isIdle()) {
					print("SIGNAL MOVE DOWN to elevator: " + e.getElevatorNumber());
					s = new SchedulerData(e.getElevatorNumber(), SchedulerData.MOVE_REQUEST, false, true,
							false);
				}
				// If elevator is below floor, move up, close doors
				else if (currentFloor < e.getRequestedFloors().get(0) && e.isIdle()) {
					print("SIGNAL MOVE UP to elevator: " + e.getElevatorNumber());
					s = new SchedulerData(e.getElevatorNumber(), SchedulerData.MOVE_REQUEST, true, false,
							false);
				}
				//If already moving towards destination floor, just tell it to continue
				else {
					print("SIGNAL CONTINUE to elevator: " + e.getElevatorNumber());
					s = new SchedulerData(e.getElevatorNumber(), SchedulerData.CONTINUE_REQUEST);
				}

			}
			break;
		case ElevatorData.DOOR_STUCK_ERROR:
			//Tell it to open/close its doors
			if (e.doorOpened()) 
				print("SIGNAL CLOSE DOORS to elevator: " + e.getElevatorNumber());
			else 
				print("SIGNAL OPEN DOORS to elevator: " + e.getElevatorNumber());
		
			s = new SchedulerData(e.getElevatorNumber(), SchedulerData.DOOR_REQUEST);
			
			break;
			
		case ElevatorData.ELEVATOR_STUCK_ERROR:
			//Elevator no longer works, and will not receive any further instructions
				print("Elevator " + e.getElevatorNumber() + ": SHUTDOWN.");
			break;
		}
		
		//Send the scheduler packet
		if (s != null)
			elevatorSend(s);
	}

	/**
	 * Clear the scheduler's floor requests
	 */
	public void clearRequest() {
		reqFloors.clear();
	}

	/**
	 * Update the scheduler's lamps
	 */
	public void updateLamps() {
		// Update the floor lamps
		floorLamps[elevDat.getCurrentFloor() - 1] = true;
	}

	/**
	 * Returns true if there is an elevator on the same floor, false otherwise
	 * @return true if there is an elevator on the same floor, false otherwise
	 */
	public boolean elevatorSameFloor(int floor) {
		potentialRoutes.clear();
		boolean caseTrue = false;
		for (int i = 0; i < elevData.length; i++) {
			//if(elevDat.isOperational()) {
				if (floor == elevData[i].getCurrentFloor()) {
					caseTrue = true;
					potentialRoutes.add(elevData[i]);
				}
			//}
		}

		return caseTrue;
	}

	/**
	 * Returns true if there is an elevator above the requested floor, false otherwise
	 * @return true if there is an elevator above the requested floor, false otherwise
	 */
	public boolean elevatorAboveFloor(int floor) {
		potentialRoutes.clear();
		boolean caseTrue = false;
		for (int i = 0; i < elevData.length; i++) {
			//if(elevData.isOperational()) {
				if (elevData[i].getCurrentFloor() > floor) {
					caseTrue = true;
				}
			//}
		}
		return caseTrue;
	}

	/**
	 * Returns true if there is an elevator below the requested floor, false otherwise
	 * @return true if there is an elevator below the requested floor, false otherwise
	 */
	public boolean elevatorBelowFloor(int floor) {
		potentialRoutes.clear();
		boolean caseTrue = false;
		for (int i = 0; i < elevData.length; i++) {
			//if(elevData[i].isOperational()) {
				if (elevData[i].getCurrentFloor() < floor) {
					caseTrue = true;
				}
			//}
		}
		return caseTrue;
	}

	/**
	 * Returns true if all the elevators are above the floor, false otherwise
	 * @return true if all the elevators are above the floor, false otherwise
	 */
	public boolean allElevatorsAboveFloor(int floor) {
		potentialRoutes.clear();
		for (int i = 0; i < elevData.length; i++) {
			potentialRoutes.add(elevData[i]);
			//if(elevData[i].isOperational()) {
				if (elevData[i].getCurrentFloor() < floor) {
					return false;
				}
			//}
		}
		return true;
	}

	/**
	 * Returns true if all the elevators are below the floor, false otherwise
	 * @return true if all the elevators are below the floor, false otherwise
	 */
	public boolean allElevatorsBelowFloor(int floor) {
		potentialRoutes.clear();
		for (int i = 0; i < elevData.length; i++) {
			potentialRoutes.add(elevData[i]);
			//if(elevData[i].isOperational()) {
				if (elevData[i].getCurrentFloor() > floor) {
					return false;
				}
			//}
		}
		return true;
	}

	public int closestElevator() {
		//Assume closest is elevator 0
		ElevatorData closest = potentialRoutes.get(0);

		for (ElevatorData e: potentialRoutes) {
			//If elevator is closer that the current closest, it becomes the new closest
			if (Math.abs((e.getCurrentFloor() - floorDat.getFloorNum())) < Math
					.abs((closest.getCurrentFloor() - floorDat.getFloorNum()))) {
				closest = e;
			} 
		}

		return closest.getElevatorNumber();

	}

	public void determineIdle() {
		ArrayList<ElevatorData> remove = new ArrayList<ElevatorData>();
		for (ElevatorData ed: potentialRoutes) {
			if(!ed.isIdle()) {// if not idle remove from potential routes
				remove.add(ed);
			}
		}
		potentialRoutes.removeAll(remove);
	}

	public void determineMovingDown() {
		ArrayList<ElevatorData> remove = new ArrayList<ElevatorData>();
		for (ElevatorData ed: potentialRoutes) {
			if(!ed.isMovingDown()) {
				remove.add(ed);
			}
		}
		potentialRoutes.removeAll(remove);
	}

	public boolean isAnyMovingDown() {
		for (ElevatorData ed: potentialRoutes) {
			if(ed.isMovingDown()) {
				return true;
			}
		}
		return false;
	}
	
	public boolean isAnyIdle() {
		for (ElevatorData ed: potentialRoutes) {
			if(ed.isIdle()) {
				return true;
			}
		}
		return false;
	}
	
	public void determineMovingUp() {
		ArrayList<ElevatorData> remove = new ArrayList<ElevatorData>();
		for (ElevatorData ed: potentialRoutes) {
			if(!ed.isMovingUp()) {
				remove.add(ed);
			}
		}
		potentialRoutes.removeAll(remove);
	}

	public boolean isAnyMovingUp() {
		for (ElevatorData ed: potentialRoutes) {
			if(ed.isMovingUp()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Determine which elevator should get the floor request
	 */
	public void routeElevator() {

		potentialRoutes = new ArrayList<ElevatorData>();
		ArrayList<FloorData> completedRequests = new ArrayList<FloorData>();
		routedElevator = -1;
		
		for(FloorData fd: pendRequests) {
			//If an elevator is on the same floor, send the first one by default
			if (elevatorSameFloor(fd.getFloorNum()) && isAnyIdle()) {
				determineIdle();
				routedElevator = potentialRoutes.get(0).getElevatorNumber(); //Return first elevator
			}
			//If all are above 
			else if (allElevatorsAboveFloor(fd.getFloorNum())) {
				if(isAnyMovingDown() && fd.downPressed()) { //determine if any elevators are moving down
					determineMovingDown(); //get rid of any elevators not moving down
					routedElevator = closestElevator(); // get the closest moving down elevator
				}
				else if(isAnyIdle()) { //is any idle
					determineIdle(); //get the idle elevators
					routedElevator = potentialRoutes.get(0).getElevatorNumber(); //Return first elevator
				}

			}

			//If all are below
			else if (allElevatorsBelowFloor(fd.getFloorNum())) {
				if(isAnyMovingUp() && fd.upPressed()) { //determine if any elevators are moving down
					determineMovingUp(); //get rid of any elevators not moving down
					routedElevator = closestElevator(); // get the closest moving down elevator
				}
				else if(isAnyIdle()) { //is any idle
					determineIdle(); //get the idle elevators
					routedElevator = potentialRoutes.get(0).getElevatorNumber(); //Return first elevator
				}
			}
			//Else, just send the closest one out of all the elevators
			else if (elevatorAboveFloor(fd.getFloorNum()) && elevatorBelowFloor(fd.getFloorNum())) {
				for (ElevatorData e: elevData) {
					potentialRoutes.add(e);
				}
				
				if(fd.upPressed() && isAnyMovingUp()) {
					determineMovingUp(); //get rid of any elevators not moving down
					routedElevator = closestElevator(); // get the closest moving down elevator
				}
				else if(fd.downPressed() && isAnyMovingDown()) {
					determineMovingDown();
					routedElevator = closestElevator();
				}
				else if(isAnyIdle()) {
					determineIdle(); //get the idle elevators
					routedElevator = potentialRoutes.get(0).getElevatorNumber(); //Return first elevator
				}

				routedElevator = closestElevator();
			}
			if(routedElevator != -1) {
				print("Sending request to Elevator " + routedElevator + ".\n");
				//Create the scheduler data object for sending 
				scheDat = new SchedulerData(routedElevator, SchedulerData.FLOOR_REQUEST, floorLamps, fd.getFloorNum(), floorDat.getDestFloor());
				elevatorSend(scheDat);
				completedRequests.add(fd);
			}
		}
		pendRequests.removeAll(completedRequests);
	}

	/**
	 * Return the last received elevator data
	 *
	 * @return
	 */
	public ElevatorData getElevatorData() {
		return elevDat;
	}

	/**
	 * Return the scheduler's current data
	 *
	 * @return the current scheduler data
	 */
	public SchedulerData getSchedulerData() {
		return scheDat;
	}

	/**
	 * Return the last received floor data
	 *
	 * @return
	 */
	public FloorData getFloorData() {
		return floorDat;
	}

	/**
	 * Prints the message on the console
	 *
	 * @param message
	 */
	public void print(String message) {
		System.out.println(message);
	}

	public static void main(String args[]) {
		//Initialize a scheduler for a system with 5 floors and 2 elevators
		Scheduler c = new Scheduler(5, 2);

		/**
		 * Scheduler Logic
		 */
		while (true) {
			c.receive();
			c.processAndSend();
			//Slow down
			c.wait(1000);
		}

	}
}
