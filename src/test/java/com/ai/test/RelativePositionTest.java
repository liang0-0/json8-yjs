package com.ai.test;

import com.ai.Y;
import com.ai.types.vo.AbsolutePosition;
import com.ai.types.ytext.YText;
import com.ai.utils.Doc;
import com.ai.utils.RelativePosition;
import com.ai.utils.undo.UndoManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RelativePositionTest {

    private void checkRelativePositions(YText ytext) {
        // test if all positions are encoded and restored correctly
        for (int i =  0; i < ytext.length(); i++) {
            // for all types of associations...
            for (int assoc = -1; assoc < 2; assoc++) {
                RelativePosition rpos = Y.createRelativePositionFromTypeIndex(ytext, i, assoc);
                int[] encodedRpos = Y.encodeRelativePosition(rpos);
                RelativePosition decodedRpos = Y.decodeRelativePosition(encodedRpos);
                AbsolutePosition absPos = Y.createAbsolutePositionFromRelativePosition(
                    decodedRpos, ytext.doc);
                
                assertEquals(i, absPos.index);
                assertEquals(assoc, absPos.assoc);
            }
        }
    }

    @Test
    public void testRelativePositionCase1() {
        Doc ydoc = new Doc();
        YText ytext = ydoc.getText();
        ytext.insert(0, "1");
        ytext.insert(0, "abc");
        ytext.insert(0, "z");
        ytext.insert(0, "y");
        ytext.insert(0, "x");
        checkRelativePositions(ytext);
    }

    @Test
    public void testRelativePositionCase2() {
        Doc ydoc = new Doc();
        YText ytext = ydoc.getText();
        ytext.insert(0, "abc");
        checkRelativePositions(ytext);
    }

    @Test
    public void testRelativePositionCase3() {
        Doc ydoc = new Doc();
        YText ytext = ydoc.getText();
        ytext.insert(0, "abc");
        ytext.insert(0, "1");
        ytext.insert(0, "xyz");
        checkRelativePositions(ytext);
    }

    @Test
    public void testRelativePositionCase4() {
        Doc ydoc = new Doc();
        YText ytext = ydoc.getText();
        ytext.insert(0, "1");
        checkRelativePositions(ytext);
    }

    @Test
    public void testRelativePositionCase5() {
        Doc ydoc = new Doc();
        YText ytext = ydoc.getText();
        ytext.insert(0, "2");
        ytext.insert(0, "1");
        checkRelativePositions(ytext);
    }

    @Test
    public void testRelativePositionCase6() {
        Doc ydoc = new Doc();
        YText ytext = ydoc.getText();
        checkRelativePositions(ytext);
    }

    @Test
    public void testRelativePositionCase7() {
        Doc docA = new Doc();
        YText textA = docA.getText("text");
        textA.insert(0, "abcde");
        
        // Create a relative position at index 2 in 'textA'
        RelativePosition relativePosition = Y.createRelativePositionFromTypeIndex(textA, 2);
        
        // Verify that the absolute positions on 'docA' are the same
        AbsolutePosition absolutePositionWithFollow = 
            Y.createAbsolutePositionFromRelativePosition(relativePosition, docA, true);
        AbsolutePosition absolutePositionWithoutFollow = 
            Y.createAbsolutePositionFromRelativePosition(relativePosition, docA, false);
            
        assertEquals(2, absolutePositionWithFollow.index);
        assertEquals(2, absolutePositionWithoutFollow.index);
    }

    @Test
    public void testRelativePositionAssociationDifference() {
        Doc ydoc = new Doc();
        YText ytext = ydoc.getText();
        ytext.insert(0, "2");
        ytext.insert(0, "1");
        
        RelativePosition rposRight = Y.createRelativePositionFromTypeIndex(ytext, 1, 0);
        RelativePosition rposLeft = Y.createRelativePositionFromTypeIndex(ytext, 1, -1);
        
        ytext.insert(1, "x");
        
        AbsolutePosition posRight = Y.createAbsolutePositionFromRelativePosition(rposRight, ydoc);
        AbsolutePosition posLeft = Y.createAbsolutePositionFromRelativePosition(rposLeft, ydoc);
        
        assertNotNull(posRight);
        assertEquals(2, posRight.index);
        
        assertNotNull(posLeft);
        assertEquals(1, posLeft.index);
    }

    @Test
    public void testRelativePositionWithUndo() {
        Doc ydoc = new Doc();
        YText ytext = ydoc.getText();
        ytext.insert(0, "hello world");
        
        RelativePosition rpos = Y.createRelativePositionFromTypeIndex(ytext, 1);
        UndoManager um = new UndoManager(ytext);
        
        ytext.delete(0, 6);
        assertEquals(0, Y.createAbsolutePositionFromRelativePosition(rpos, ydoc).index);
        
        um.undo();
        assertEquals(1, Y.createAbsolutePositionFromRelativePosition(rpos, ydoc).index);
        
        AbsolutePosition posWithoutFollow = Y.createAbsolutePositionFromRelativePosition(rpos, ydoc, false);
        System.out.println("posWithoutFollow = " + posWithoutFollow);
        assertEquals(6, Y.createAbsolutePositionFromRelativePosition(rpos, ydoc, false).index);
        
        Doc ydocClone = new Doc();
        Y.applyUpdate(ydocClone, Y.encodeStateAsUpdate(ydoc));
        
        assertEquals(6, Y.createAbsolutePositionFromRelativePosition(rpos, ydocClone).index);
        assertEquals(6, Y.createAbsolutePositionFromRelativePosition(rpos, ydocClone, false).index);
    }
}