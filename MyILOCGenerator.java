package edu.jmu.decaf;

import java.util.*;

import static edu.jmu.decaf.ILOCOperand.*;
import static edu.jmu.decaf.ILOCInstruction.Form.*;

/**
 * Concrete ILOC generator class.
 *
 * authors: Elena Trafton and Ben Bradberry
 */
public class MyILOCGenerator extends ILOCGenerator
{
    private Stack<ILOCOperand> checkWhileLabels;
    private Stack<ILOCOperand> bodyWhileLabels;
    private Stack<ILOCOperand> endWhileLabels;

    private ArrayList<ILOCOperand> callLabels; //functions

    MyILOCGenerator() {
        checkWhileLabels = new Stack<>();
        bodyWhileLabels = new Stack<>();
        endWhileLabels = new Stack<>();

        callLabels = new ArrayList<>();
    }


    /*
    QUESTIONS:
    how does a VoidFuncCall differ from a FuncCall?
    strings are ONLY for print functions; I can't declare a string variable?

    NOTES:
    variable postvisit: I don't think I need to do anything here because I've already allocated space in ASTFunction.

        for function call:
            copy code
            create temp reg
            assign variables to reg
            find destination
            CALL destination

    TO-DO:
    -variable/location/assignment: pretty trivial
    -funccall and voidfunccall: gonna be brutal, lots of debugging
    -TEST EVERYTHING (except recursion)

    THINGS TO TEST:
    -variable declarations
    -variable accesses
    -nested if statements
    -nested while loops
    -multiple functions
    etc.
     */

    @Override
    public void postVisit(ASTFunctionCall node) {
        //TODO

        //hoooooboy
    }

    @Override
    public void postVisit(ASTVoidFunctionCall node) {
        if (node.name.equals("print_str")) {
            String str = ((ASTLiteral)node.arguments.get(0)).value.toString();
            emit(node, PRINT, newStrConstant(str));
        } else if (node.name.equals("print_int")) {
            Integer in = (Integer)((ASTLiteral)node.arguments.get(0)).value;
            emit(node, PRINT, newIntConstant(in));
        } else if (node.name.equals("print_bool")) {
            Boolean bool = (Boolean)((ASTLiteral)node.arguments.get(0)).value;
            emit(node, PRINT, newStrConstant(bool.toString()));
        }

        // todo
        //likely similar to ASTFunctionCall
        //hoooooooboy
    }

    //

    /**
     * ILOC code for a variable access. uses the provided emitLoad() function.
     */
    @Override
    public void postVisit(ASTLocation node) { //access
        setTempReg(node, emitLoad(node));
    }

    /**
     * ILOC code for a variable assignment. use the provided emitStore() function
     */
    @Override
    public void postVisit(ASTAssignment node) {
        copyCode(node, node.value);
        ILOCOperand source = getTempReg(node.value);
        emitStore(node, source);

        /*
        copyCode(node, node.value);
        ILOCOperand source = getTempReg(node.value);
        copyCode(node, node.location);
        ILOCOperand dest = getTempReg(node.location);

        emit(node, I2I, source, dest);
        */
    }

    /**
     * the only previsit function (for now) //todo check this at the end
     * creates and pushes labels relevant to the immediate while loop.
     */
    @Override
    public void preVisit(ASTWhileLoop node) {
        checkWhileLabels.push(newAnonymousLabel());
        bodyWhileLabels.push(newAnonymousLabel());
        endWhileLabels.push(newAnonymousLabel());
    }

    /**
     * ILOC code for a while loop. uses the previously created/modified stacks to find the relevant
     * labels to which to jump and branch.
     */
    @Override
    public void postVisit(ASTWhileLoop node) {
        emit(node, LABEL, checkWhileLabels.peek()); //check
        copyCode(node, node.guard);

        ILOCOperand rE = getTempReg(node.guard); //compare
        emit(node, CBR, rE, bodyWhileLabels.peek(), endWhileLabels.peek());

        emit(node, LABEL, bodyWhileLabels.peek()); //body
        copyCode(node, node.body);
        emit(node, JUMP, checkWhileLabels.peek());

        emit(node, LABEL, endWhileLabels.peek()); //done
    }

    /**
     * ILOC code for a break statement.
     * simply jump to the end of the innermost (at that moment) while loop.
     */
    @Override
    public void postVisit(ASTBreak node) {
        emit(node, JUMP, endWhileLabels.peek());
    }


    /**
     * ILOC code for a break statement.
     * simply jump to the beginner of the innermost (at that moment) while loop.
     */
    @Override
    public void postVisit(ASTContinue node) {
        emit(node, JUMP, checkWhileLabels.peek());
    }

    /**
     * ILOC code for an ASTLiteral
     * if the literal is an integer, just load it.
     * if the literal is a string, you've found an alien.
     * if the literal is a bool, load it as 0 or 1 accordingly.
     */
    @Override
    public void postVisit(ASTLiteral node) {
        ILOCOperand destReg = ILOCOperand.newVirtualReg();

        switch (node.type) {
            case INT:
                emit(node, LOAD_I, ILOCOperand.newIntConstant((Integer) node.value), destReg);
                break;
            case STR:
                addComment(node, "String variables shouldn't exist");
                break;
            case BOOL:
                if ((Boolean) (node.value) == false) {
                    emit(node, LOAD_I, newIntConstant(0), destReg);
                } else {
                    emit(node, LOAD_I, newIntConstant(1), destReg);
                }
                break;
        }
        setTempReg(node, destReg);
    }

    /**
     * ILOC code for an if-statement. may or may not also include an "else".
     */
    @Override
    public void postVisit(ASTConditional node) {
        copyCode(node, node.condition);
        ILOCOperand rE = getTempReg(node.condition);

        ILOCOperand ifLabel = newAnonymousLabel(); //if X then Y
        ILOCOperand doneLabel = newAnonymousLabel();

        if (node.elseBlock == null) {
            emit(node, CBR, rE, ifLabel, doneLabel);
            emit(node, LABEL, ifLabel);
            copyCode(node, node.ifBlock);
        } else {
            ILOCOperand elseLabel = newAnonymousLabel();
            emit(node, CBR, rE, ifLabel, elseLabel);
            emit(node, LABEL, ifLabel);
            copyCode(node, node.ifBlock);
            emit(node, JUMP, doneLabel);
            emit(node, LABEL, elseLabel);
            copyCode(node, node.elseBlock);
        }
        emit(node, LABEL, doneLabel);
    }


    /**
     * ILOC code for an ASTFunction.
     * emit the prologue, then any code from the body of the function.
     */
    @Override
    public void postVisit(ASTFunction node)
    {
        emit(node, PUSH, REG_BP);
        addComment(node, "start prologue");
        emit(node, I2I, REG_SP, REG_BP);
        emitLocalVarStackAdjustment(node);
        addComment(node, "end prologue");

        copyCode(node, node.body); // propagate code from body block to the function level

        //no epilogue because that happens in the ASTReturn instead TODO change this comment/figure this out
        if (node.returnType == ASTNode.DataType.VOID) {
            emit(node, I2I, REG_BP, REG_SP);
            addComment(node, "start epilogue at the end of a void function");
            emit(node, POP, REG_BP);
            emit(node, RETURN);
            addComment(node, "end epilogue at the end of a void function");
        }
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
        addComment(node, "start epilogue");
        emit(node, POP, REG_BP);
        emit(node, RETURN);
        addComment(node, "end epilogue");
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
                addComment(node, "You found an easter egg!");
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
                addComment(node, "You found an easter egg!");
                break;
        }
        setTempReg(node, destReg);
    }
}
