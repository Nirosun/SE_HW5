/**
 *  The ranked Boolean retrieval model has no parameters.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

public class RetrievalModelIndri extends RetrievalModel {
  
  protected double mu = 2500;
  protected double lambda = 0.4;

  /**
   * Set a retrieval model parameter.
   * @param parameterName
   * @param parametervalue
   * @return Always false because this retrieval model has no parameters.
   */
  public boolean setParameter (String parameterName, double value) {
	if (parameterName.equals("mu")) {
	  mu = value;
	  return true;
	}
	else if (parameterName.equals("lambda")) {
	  lambda = value;
	  return true;
	}
	else {
      System.err.println ("Error: Unknown parameter name for retrieval model " +
			"Indri: " +
			parameterName);
	}
    return false;
  }

  /**
   * Set a retrieval model parameter.
   * @param parameterName
   * @param parametervalue
   * @return Always false because this retrieval model has no parameters.
   */
  public boolean setParameter (String parameterName, String value) {
	if (parameterName.equals("mu")) {
	  mu = Double.parseDouble(value);
	  return true;
	}
	else if (parameterName.equals("lambda")) {
	  lambda = Double.parseDouble(value);
	  return true;
	}
	else {
      System.err.println ("Error: Unknown parameter name for retrieval model " +
			"Indri: " +
			parameterName);
	}
    return false;
  }

}
