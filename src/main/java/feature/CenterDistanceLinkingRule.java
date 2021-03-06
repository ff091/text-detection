package feature;

import validator.DValidator;

/**
 * Only permits links between features that have a valid center to center
 * distance.
 * 
 * @author PilgerstorferP
 * 
 */
public class CenterDistanceLinkingRule extends LinkingRule {
	private DValidator distance;

	public CenterDistanceLinkingRule(DValidator distance) {
		this.distance = distance;
	}

	@Override
	public boolean link(Feature f0, Feature f1) {
		double dist = f0.getCenter().distance(f1.getCenter());
		return distance.isValid(dist);
	}
}
