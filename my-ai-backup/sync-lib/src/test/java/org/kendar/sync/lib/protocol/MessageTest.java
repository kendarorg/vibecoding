package org.kendar.sync.lib.protocol;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void testSerializeAndDeserialize() throws IOException {
        // Create a test message
        ErrorMessage originalMessage = new ErrorMessage("ERR001", "Test error message", "Test details");
        
        // Serialize the message
        byte[] serialized = originalMessage.serialize();
        
        // Deserialize the message
        Message deserializedMessage = Message.deserialize(serialized);
        
        // Verify the deserialized message
        assertNotNull(deserializedMessage);
        assertTrue(deserializedMessage instanceof ErrorMessage);
        
        ErrorMessage deserializedErrorMessage = (ErrorMessage) deserializedMessage;
        assertEquals(MessageType.ERROR, deserializedErrorMessage.getMessageType());
        assertEquals("ERR001", deserializedErrorMessage.getErrorCode());
        assertEquals("Test error message", deserializedErrorMessage.getErrorMessage());
        assertEquals("Test details", deserializedErrorMessage.getDetails());
    }
    
    @Test
    void testDeserializeWithSpecificType() throws IOException {
        // Create a test message
        ErrorMessage originalMessage = new ErrorMessage("ERR001", "Test error message", "Test details");
        
        // Serialize the message
        byte[] serialized = originalMessage.serialize();
        
        // Deserialize the message with a specific type
        ErrorMessage deserializedMessage = Message.deserialize(serialized, ErrorMessage.class);
        
        // Verify the deserialized message
        assertNotNull(deserializedMessage);
        assertEquals(MessageType.ERROR, deserializedMessage.getMessageType());
        assertEquals("ERR001", deserializedMessage.getErrorCode());
        assertEquals("Test error message", deserializedMessage.getErrorMessage());
        assertEquals("Test details", deserializedMessage.getDetails());
    }
}