/**
 *  The ranked Boolean retrieval model has no parameters.
 *
 *  Copyright (c) 2014, Carnegie Mellon University.  All Rights Reserved.
 */

public class RetrievalModelBM25 extends RetrievalModel {
  
  protected double k_1 = 1.2;
  protected double b = 0.75;
  protected double k_3 = 0;
  
  /**
   * Set a retrieval model parameter.
   * @param parameterName
   * @param parametervalue
   * @return Always false because this retrieval model has no parameters.
   */
  public boolean setParameter (String parameterName, double value) {
	if (parameterName.equals("k_1")) {
	  k_1 = value;
	  return true;
	}
	else if (parameterName.equals("b")) {
	  b = value;
	  return true;
	}
	else if(parameterName.equals("k_3")) {
	  k_3 = value;
	  return true;
	}
	else {
      System.err.println ("Error: Unknown parameter name for retrieval model " +
			"BM25: " +
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
	if (parameterName.equals("k_1")) {
	  k_1 = Double.parseDouble(value);
	  return true;
	}
	else if (parameterName.equals("b")) {
	  b = Double.parseDouble(value);
	  return true;
	}
	else if(parameterName.equals("k_3")) {
	  k_3 = Double.parseDouble(value);
	  return true;
	}
	else {
      System.err.println ("Error: Unknown parameter name for retrieval model " +
			"BM25: " +
			parameterName);
	}
    return false;
  }

}
