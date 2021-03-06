package partitionchecker;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 * Simple case where we are accessing the fields of Datum object directly
 * without method calls.
 * 
 */
public class Project1 {

	public static void main(String[] args) {
		List<Datum> data = new ArrayList<Datum>();
		data.add(new Datum(new Integer(1)));

		for (Datum d : data) {

			// Begin Stage1
			Integer field = d.field;
			// End Stage1

			// Begin Stage2
			Integer manipulatedField = produce(field);
			// End Stage2

			// Begin Stage3
			d.field = manipulatedField;
			// End Stage3
		}
	}

	static Integer produce(Integer input) {
		return input + 2;

	}
}
