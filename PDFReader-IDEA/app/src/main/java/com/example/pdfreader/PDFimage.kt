package com.example.pdfreader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.MotionEvent
import android.widget.ImageView


@SuppressLint("AppCompatCustomView")
class PDFimage (context: Context?) : ImageView(context) {

    class CommandWrapper{
        var thePath = SerializablePath()
        var theBrush = 0
        var Erased = false
    }

    lateinit var referenceToMain: MainActivity

    var listOfCommands = mutableListOf<CommandWrapper?>()

    var listOfUndos = mutableListOf<CommandWrapper?>()

    val LOGNAME = "pdf_image"

    // drawing path
    var path: SerializablePath? = null
    var paths = mutableListOf<SerializablePath?>()
    var brushesforpaths = mutableListOf<Int?>()

    // image to display
    var bitmap: Bitmap? = null
    var paint = Paint()


    private var mX = 0f
    private var mY= 0f

    var PEN_BRUSH = 0;

    var HIGHLIGHT_BRUSH = 1;

    var ERASER_BRUSH = 2;

    var current_brush = 0;

    var erasepath = Path()

    var multiTouchMove = true


    // for panning
    var x1 = 0f
    var x2 = 0f
    var y1 = 0f
    var y2 = 0f
    var old_x1 = 0f
    var old_y1 = 0f
    var old_x2 = 0f
    var old_y2 = 0f
    var mid_x = -1f
    var mid_y = -1f
    var old_mid_x = -1f
    var old_mid_y = -1f
    var p1_id = 0
    var p1_index = 0
    var p2_id = 0
    var p2_index = 0

    // store cumulative transformations
    // the inverse matrix is used to align points with the transformations - see below
    var currentMatrix = Matrix()
    var inverse = Matrix()

    var pathOffsetx = 0;
    var pathOffsety = 0;


    private fun touchStart(x: Float, y: Float) {
        if(current_brush != ERASER_BRUSH) {
            path = SerializablePath()
            paths.add(path)
            brushesforpaths.add(current_brush)

            var commandToAdd = CommandWrapper()
            commandToAdd.thePath = path as SerializablePath
            commandToAdd.theBrush = current_brush

            listOfCommands.add(commandToAdd)

            referenceToMain.undoButtonObject.setAlpha(1f)
            referenceToMain.undoButtonObject.setClickable(true)
            referenceToMain.redoButtonObject.setAlpha(0.5f)
            referenceToMain.redoButtonObject.setClickable(false)
            listOfUndos.clear()

            path!!.reset()


            path!!.moveTo(x, y)

            mX = x
            mY = y

        } else {
            erasepath = SerializablePath()

            erasepath!!.reset()


            erasepath!!.moveTo(x, y)

            mX = x
            mY = y

        }
    }

    private fun touchMove(x: Float, y: Float) {
        if(current_brush != ERASER_BRUSH) {
            path?.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2)
            path?.addPathPoints(floatArrayOf(mX, mY, (x + mX) / 2, (y + mY) / 2))
            mX = x
            mY = y

        } else {
            erasepath?.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2)
            mX = x
            mY = y
        }

    }

    private fun touchUp() {
        if(current_brush != ERASER_BRUSH) {
            path?.lineTo(mX, mY)
        } else {
            erasepath?.lineTo(mX, mY)
        }
    }




    // capture touch events (down/move/up) to create a path
    // and use that to create a stroke that we can draw
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        var x = event.x
        var y = event.y

        var startingx = 0f
        var startingy = 0f

        if(multiTouchMove) {
            var inverted = floatArrayOf()
            when(event.pointerCount) {
                1 -> {

                    // mapPoints returns values in-place
                    inverted = floatArrayOf(event.getX(), event.getY())
                    inverse.mapPoints(inverted)

                    // first pass, initialize the old == current value
                    if (old_x1 < 0 || old_y1 < 0) {
                        x1 = inverted.get(0)
                        old_x1 = x1
                        y1 = inverted.get(1)
                        old_y1 = y1
                    } else {
                        old_x1 = x1
                        old_y1 = y1
                        x1 = inverted.get(0)
                        y1 = inverted.get(1)
                    }


                    // distance
                    val d_old =
                        Math.sqrt(Math.pow((old_x1 - old_x2).toDouble(), 2.0) + Math.pow((old_y1 - old_y2).toDouble(), 2.0))
                            .toFloat()
                    val d = Math.sqrt(Math.pow((x1 - x2).toDouble(), 2.0) + Math.pow((y1 - y2).toDouble(), 2.0))
                        .toFloat()

                    // pan and zoom during MOVE event
                    if (event.action == MotionEvent.ACTION_MOVE) {
                        Log.d(LOGNAME, "Multitouch move")
                        // pan == translate of midpoint
                        val dx = x1 - old_x1
                        val dy = y1 - old_y1
                        currentMatrix.preTranslate(dx, dy)
                        Log.d(LOGNAME, "translate: $dx,$dy")

                        // reset on up
                    } else if (event.action == MotionEvent.ACTION_UP) {
                        old_x1 = -1f
                        old_y1 = -1f
                    }
                }
                2 -> {
                    // point 1
                    p1_id = event.getPointerId(0)
                    p1_index = event.findPointerIndex(p1_id)

                    // mapPoints returns values in-place
                    inverted = floatArrayOf(event.getX(p1_index), event.getY(p1_index))
                    inverse.mapPoints(inverted)

                    // first pass, initialize the old == current value
                    if (old_x1 < 0 || old_y1 < 0) {
                        x1 = inverted.get(0)
                        old_x1 = x1
                        y1 = inverted.get(1)
                        old_y1 = y1
                    } else {
                        old_x1 = x1
                        old_y1 = y1
                        x1 = inverted.get(0)
                        y1 = inverted.get(1)
                    }

                    // point 2
                    p2_id = event.getPointerId(1)
                    p2_index = event.findPointerIndex(p2_id)

                    // mapPoints returns values in-place
                    inverted = floatArrayOf(event.getX(p2_index), event.getY(p2_index))
                    inverse.mapPoints(inverted)

                    // first pass, initialize the old == current value
                    if (old_x2 < 0 || old_y2 < 0) {
                        x2 = inverted.get(0)
                        old_x2 = x2
                        y2 = inverted.get(1)
                        old_y2 = y2
                    } else {
                        old_x2 = x2
                        old_y2 = y2
                        x2 = inverted.get(0)
                        y2 = inverted.get(1)
                    }

                    // midpoint
                    mid_x = (x1 + x2) / 2
                    mid_y = (y1 + y2) / 2
                    old_mid_x = (old_x1 + old_x2) / 2
                    old_mid_y = (old_y1 + old_y2) / 2

                    // distance
                    val d_old =
                        Math.sqrt(Math.pow((old_x1 - old_x2).toDouble(), 2.0) + Math.pow((old_y1 - old_y2).toDouble(), 2.0))
                            .toFloat()
                    val d = Math.sqrt(Math.pow((x1 - x2).toDouble(), 2.0) + Math.pow((y1 - y2).toDouble(), 2.0))
                        .toFloat()

                    // pan and zoom during MOVE event
                    if (event.action == MotionEvent.ACTION_MOVE) {
                        Log.d(LOGNAME, "Multitouch move")
                        // pan == translate of midpoint
                        val dx = mid_x - old_mid_x
                        val dy = mid_y - old_mid_y
                        currentMatrix.preTranslate(dx, dy)
                        Log.d(LOGNAME, "translate: $dx,$dy")

                        // zoom == change of spread between p1 and p2
                        var scale = d / d_old
                        scale = Math.max(0f, scale)
                        currentMatrix.preScale(scale, scale, mid_x, mid_y)
                        Log.d(LOGNAME, "scale: $scale")

                        // reset on up
                    } else if (event.action == MotionEvent.ACTION_UP) {
                        old_x1 = -1f
                        old_y1 = -1f
                        old_x2 = -1f
                        old_y2 = -1f
                        old_mid_x = -1f
                        old_mid_y = -1f
                    }
                }
            }

            return true

        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {

                if(current_brush != ERASER_BRUSH) {
                    touchStart(x, y);
                    invalidate();

                    return true
                } else {

                    val result = Path()

                    touchStart(x, y);
                    invalidate();

                    var removehere = -1;

                    for ((j, path) in paths.withIndex()) {
                        if (path != null) {
                            result.op(path, erasepath, Path.Op.INTERSECT)
                        };
                        if(!result.isEmpty) {
                            removehere = j;
                        }
                    }
                    if(removehere != -1) {
                        var commandToAdd = CommandWrapper()
                        commandToAdd.thePath = paths[removehere]!!
                        commandToAdd.theBrush = brushesforpaths[removehere]!!
                        commandToAdd.Erased = true


                        listOfCommands.add(commandToAdd)

                        referenceToMain.undoButtonObject.setAlpha(1f)
                        referenceToMain.undoButtonObject.setClickable(true)
                        referenceToMain.redoButtonObject.setAlpha(0.5f)
                        referenceToMain.redoButtonObject.setClickable(false)
                        listOfUndos.clear()


                        paths.removeAt(removehere)
                        brushesforpaths.removeAt(removehere)
                    }

                }
            }

            MotionEvent.ACTION_MOVE -> {
                if(current_brush != ERASER_BRUSH) {
                    touchMove(x, y);
                    invalidate();

                    return true
                } else {
                    val result = Path()

                    touchMove(x, y);
                    invalidate();

                    var removehere = -1;

                    for ((j, path) in paths.withIndex()) {
                        if (path != null) {
                            result.op(path, erasepath, Path.Op.INTERSECT)
                        };
                        if(!result.isEmpty) {
                            removehere = j;
                        }
                    }
                    if(removehere != -1) {
                        var commandToAdd = CommandWrapper()
                        commandToAdd.thePath = paths[removehere]!!
                        commandToAdd.theBrush = brushesforpaths[removehere]!!
                        commandToAdd.Erased = true


                        listOfCommands.add(commandToAdd)

                        referenceToMain.undoButtonObject.setAlpha(1f)
                        referenceToMain.undoButtonObject.setClickable(true)
                        referenceToMain.redoButtonObject.setAlpha(0.5f)
                        referenceToMain.redoButtonObject.setClickable(false)
                        listOfUndos.clear()


                        paths.removeAt(removehere)
                        brushesforpaths.removeAt(removehere)
                    }
                }

            }

            MotionEvent.ACTION_UP -> {
                if(current_brush != ERASER_BRUSH) {
                    touchUp();
                    invalidate();

                    return true

                } else {
                    val result = Path()

                    touchUp();
                    invalidate();

                    var removehere = -1;

                    for ((j, path) in paths.withIndex()) {
                        if (path != null) {
                            result.op(path, erasepath, Path.Op.INTERSECT)
                        };
                        if(!result.isEmpty) {
                            removehere = j;
                        }
                    }
                    if(removehere != -1) {

                        var commandToAdd = CommandWrapper()
                        commandToAdd.thePath = paths[removehere]!!
                        commandToAdd.theBrush = brushesforpaths[removehere]!!
                        commandToAdd.Erased = true

                        listOfCommands.add(commandToAdd)

                        referenceToMain.undoButtonObject.setAlpha(1f)
                        referenceToMain.undoButtonObject.setClickable(true)
                        referenceToMain.redoButtonObject.setAlpha(0.5f)
                        referenceToMain.redoButtonObject.setClickable(false)
                        listOfUndos.clear()


                        paths.removeAt(removehere)
                        brushesforpaths.removeAt(removehere)
                    }
                }
            }
        }
        return true
    }


    // set image as background
    fun setImage(bitmap: Bitmap?) {
        this.bitmap = bitmap
    }

    // set brush characteristics
    // e.g. color, thickness, alpha
    fun setBrush(brushType: Int) {
        if(brushType == 0) {
            paint.setAntiAlias(true)
            paint.setColor(Color.BLUE)
            paint.setStyle(Paint.Style.STROKE)
            paint.setStrokeJoin(Paint.Join.ROUND)
            paint.setStrokeCap(Paint.Cap.ROUND)
            paint.setStrokeWidth(10f)

            // 0xff=255 in decimal
            paint.setAlpha(0xff)
        } else if (brushType == 1){
            paint.setColor(Color.YELLOW)
            paint.setStrokeCap(Paint.Cap.BUTT)
            paint.setStrokeWidth(20f)

            // 0xff=255 in decimal
            paint.setAlpha(100)
        } else {
            paint.setColor(0x00000000);
            paint.setAlpha(0x00);

        }
    }

    override fun onDraw(canvas: Canvas) {

        canvas.concat(currentMatrix)

        setBrush(current_brush)

        // draw background
        if (bitmap != null) {
            setImageBitmap(bitmap)
        }
        // draw lines over it
        var i = 0;
        for (path in paths) {
            if (path != null) {
                if(brushesforpaths[i] == PEN_BRUSH) {
                    setBrush(PEN_BRUSH)
                } else if(brushesforpaths[i] == HIGHLIGHT_BRUSH) {
                    setBrush(HIGHLIGHT_BRUSH)
                } else {
                    setBrush(ERASER_BRUSH)

                }

                var p = Path()

                p.addPath(path, inverse);

                canvas.drawPath(p, paint)

                invalidate()

                i++
            }
        }

        super.onDraw(canvas)

    }

}
