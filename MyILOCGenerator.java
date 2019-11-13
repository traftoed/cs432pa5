package edu.jmu.decaf;

import java.util.*;

import static edu.jmu.decaf.ILOCOperand.*;
import static edu.jmu.decaf.ILOCInstruction.Form.*;

/**
 * Concrete ILOC generator class.
 *
 * authors: Ben Bradberry and Elena Trafton
 */
public class MyILOCGenerator extends ILOCGenerator
{

    /*
    QUESTIONS ETC:

    how does a VoidFuncCall differ from a FuncCall?
    what is setTempReg?
     */

    @Override
    public void postVisit(ASTWhileLoop node) {
        //TODO
        //use CodeGenTemplates.pdf on Canvas Files
    }

    @Override
    public void postVisit(ASTBreak node) {
        // todo
        // use CodeGenTemplates.pdf on Canvas Files
    }

    @Override
    public void postVisit(ASTConditional node) {
        // todo
        // use CodeGenTemplates.pdf on Canvas Files
    }


    @Override
    public void postVisit(ASTContinue node) {
        // todo
        // use CodeGenTemplates.pdf on Canvas Files
    }

    @Override
    public void postVisit(ASTVariable node) {
        //TODO
        //allocate space
    }

    @Override
    public void postVisit(ASTLocation node) {
        //TODO
        //load into register? or just let everything be handled by ASTAssignment
    }

    @Override
    public void postVisit(ASTAssignment node) {
        //TODO
        //plunk evaluation of expression into appropriate location

    }

    @Override
    public void postVisit(ASTFunctionCall node) {
        //TODO
        //hoooooboy
    }

    @Override
    public void postVisit(ASTVoidFunctionCall node) {
        // todo: Might be a copy of postvisit with ASTFunctionCall
        //hoooooooboy
    }


    /**
     * ILOC code for an ASTFunction.
     * emit the prologue, then any code from the body of the function.
     */
    @Override
    public void postVisit(ASTFunction node)
    {
        emit(node, PUSH, REG_BP);
        emit(node, I2I, REG_SP, REG_BP);
        emitLocalVarStackAdjustment(node);
        addComment(node, "prologue");

        copyCode(node, node.body); // propagate code from body block to the function level

        //no epilogue because that happens in the ASTReturn instead
    }

    /**
     * ILOC code for an ASTBlock. Nothing interesting happens at a block;
     * everything interesting happens in a block. So just copy everything from inside the block up the tree.
     */
    @Override
    public void postVisit(ASTBlock node)
    {
        // concatenate the generated code for all child statements
        for (ASTStatement s : node.statements) {
            copyCode(node, s);
        }
    }

    /**
     * ILOC code for an ASTReturn. save the return value and emit the epilogue.
     */
    @Override
    public void postVisit(ASTReturn node)
    {
        if (node.hasValue()) {
            copyCode(node, node.value);
            emit(node, ILOCInstruction.Form.I2I, getTempReg(node.value), ILOCOperand.REG_RET);
        }

        emit(node, I2I, REG_BP, REG_SP);
        emit(node, POP, REG_BP);
        emit(node, RETURN);
        addComment(node, "epilogue");

        emit(node, ILOCInstruction.Form.RETURN);
    }

    /**
     * ILOC code for an ASTLiteral. just load it.
     */
    @Override
    public void postVisit(ASTLiteral node) {
        ILOCOperand destReg = ILOCOperand.newVirtualReg();
        emit(node, LOAD_I, ILOCOperand.newIntConstant(((Integer)node.value)), destReg);
        setTempReg(node, destReg);
    }

    /**
     * ILOC code for a unary expression.
     * evaluate the child expression and then perform the designated operation on it.
     */
    @Override public void postVisit(ASTUnaryExpr node) {
        ILOCOperand child = getTempReg(node.child);
        ILOCOperand destReg = ILOCOperand.newVirtualReg(); // Is this needed?
        copyCode(node, node.child);

        switch (node.operator) {
            case NEG:
                emit(node, ILOCInstruction.Form.NEG, child, destReg);
                break;
            case NOT:
                emit(node, ILOCInstruction.Form.NOT, child, destReg);
                break;
            default:
                // Program should not be able to get here
                System.out.println("You found an easter egg!");
                break;
        }


        setTempReg(node, destReg);
    }

    /**
     * ILOC code for a binary expression. evaluate left and right children and then perform
     * the designated operation on them.
     */
    @Override public void postVisit(ASTBinaryExpr node) {
        ILOCOperand leftReg = getTempReg(node.leftChild);
        ILOCOperand rightReg = getTempReg(node.rightChild);
        ILOCOperand destReg = ILOCOperand.newVirtualReg();
        copyCode(node, node.leftChild);
        copyCode(node, node.rightChild);

        switch (node.operator) {
            case OR: emit(node, ILOCInstruction.Form.OR, leftReg, rightReg, destReg);
                break;
            case AND:
                emit(node, ILOCInstruction.Form.AND, leftReg, rightReg, destReg);
                break;
            case EQ:
                emit(node, ILOCInstruction.Form.CMP_EQ, leftReg, rightReg, destReg);
                break;
            case NE:
                emit(node, ILOCInstruction.Form.CMP_NE, leftReg, rightReg, destReg);
                break;
            case LT:
                emit(node, ILOCInstruction.Form.CMP_LT, leftReg, rightReg, destReg);
                break;
            case GT:
                emit(node, ILOCInstruction.Form.CMP_GT, leftReg, rightReg, destReg);
                break;
            case LE:
                emit(node, ILOCInstruction.Form.CMP_LE, leftReg, rightReg, destReg);
                break;
            case GE:
                emit(node, ILOCInstruction.Form.CMP_GE, leftReg, rightReg, destReg);
                break;
            case ADD:
                emit(node, ILOCInstruction.Form.ADD, leftReg, rightReg, destReg);
                break;
            case SUB:
                emit(node, ILOCInstruction.Form.SUB, leftReg, rightReg, destReg);
                break;
            case MUL:
                emit(node, ILOCInstruction.Form.MULT, leftReg, rightReg, destReg);
                break;
            case DIV:
                emit(node, ILOCInstruction.Form.DIV, leftReg, rightReg, destReg);
                break;
            case MOD:
                ILOCOperand tempReg1 = ILOCOperand.newVirtualReg();
                ILOCOperand tempReg2 = ILOCOperand.newVirtualReg();

                // Divide
                emit(node, ILOCInstruction.Form.DIV, leftReg, rightReg, tempReg1);
                // Multiply
                emit(node, ILOCInstruction.Form.MULT, rightReg, tempReg1, tempReg2);
                // Subtract
                emit(node, ILOCInstruction.Form.SUB, leftReg, tempReg2, destReg);
                addComment(node, "mod doesn't  exist in ILOC so we gotta do some janky stuff");

                break;
            default:
                // Should never get here
                System.out.println("You found an easter egg!");
                break;
        }
        setTempReg(node, destReg);
    }


}
