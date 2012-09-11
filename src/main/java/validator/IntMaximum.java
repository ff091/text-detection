package validator;

/**
 * An integer has to be lower or equal than a specified maximum value to be
 * valid
 * 
 * @author PilgerstorferP
 * 
 */
public class IntMaximum implements IntValidator {
	private int max;

	public IntMaximum(int max) {
		this.max = max;
	}

	public int getMax() {
		return max;
	}

	public boolean isValid(int value) {
		return value <= max;
	}
}
