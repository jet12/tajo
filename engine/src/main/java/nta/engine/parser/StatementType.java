/**
 * 
 */
package nta.engine.parser;

/**
 * @author Hyunsik Choi
 *
 */
public enum StatementType {
	// Store
  STORE,
  // Select
	SELECT,
	
	// Update
	INSERT,
	UPDATE,
	DELETE,
	
	// Schema	
	CREATE_TABLE,	
	DROP_TABLE,
	
	// INDEX
	CREATE_INDEX,
	
	// Control
	SHOW_TABLES,
	DESC_TABLE,
	SHOW_FUNCTION;
}
