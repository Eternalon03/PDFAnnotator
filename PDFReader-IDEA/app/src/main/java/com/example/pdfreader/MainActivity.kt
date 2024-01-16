package com.example.pdfreader

import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.ObjectOutputStream


// PDF sample code from
// https://medium.com/@chahat.jain0/rendering-a-pdf-document-in-android-activity-fragment-using-pdfrenderer-442462cb8f9a
class MainActivity : AppCompatActivity() {

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d("here", "ff")

        val display = windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        val width = size.x
        val height = size.y
        pageImage.minimumWidth = width
        pageImage.minimumHeight = height-250

        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            pageImage.currentMatrix.preScale(2f, 2f)
            pageImage.currentMatrix.preTranslate(-650f, 0f)
        } else {
            pageImage.currentMatrix = Matrix()
        }
    }

    class PageDrawingWrapper{
        var listOfPaths = mutableListOf<SerializablePath?>()
        var listOfBrushes = mutableListOf<Int?>()
        var pageNumberForDraw = -1;
    }

    class AllCommandsWrapper{
        var listOfCommands = mutableListOf<PDFimage.CommandWrapper?>()
        var listOfUndos = mutableListOf<PDFimage.CommandWrapper?>()
        var pageNumberForCommand = -1;
    }

    var AllPageDrawings = mutableListOf<PageDrawingWrapper?>()

    var AllPageCommands = mutableListOf<AllCommandsWrapper?>()

    // if i put r elements here it fucking breaks

    val LOGNAME = "pdf_viewer"
    val FILENAME = "shannon1948.pdf"
    val FILERESID = R.raw.shannon1948

    // manage the pages of the PDF, see below
    lateinit var pdfRenderer: PdfRenderer
    lateinit var parcelFileDescriptor: ParcelFileDescriptor
    var currentPage: PdfRenderer.Page? = null

    // custom ImageView class that captures strokes and draws them over the image
    lateinit var pageImage: PDFimage

    var pageNumber = 0;
    var totalpages = 1;

    lateinit var undoButtonObject: Button
    lateinit var redoButtonObject: Button


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        //added by me, change xml file?
        val topBarView = findViewById<View>(R.id.topBar) as TextView
        topBarView.setText(" " + FILENAME);
        val bottomBarView = findViewById<View>(R.id.bottomBar) as TextView
        bottomBarView.setText("Page Number: " + (pageNumber+1) + "/" + totalpages);

        val layout = findViewById<LinearLayout>(R.id.pdfLayout)
        layout.isEnabled = true

        pageImage = PDFimage(this)
        layout.addView(pageImage)

        val display = windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        val width = size.x
        val height = size.y
        pageImage.minimumWidth = width
        pageImage.minimumHeight = height-250

        pageImage.referenceToMain = this

        val backButtonObject = findViewById<View>(R.id.backButton) as Button
        val nextButtonObject = findViewById<View>(R.id.nextButton) as Button

        backButtonObject.apply {
            setOnClickListener {
                if(pageNumber != 0)  {

                    pageImage.currentMatrix = Matrix()

                    var drawingStateToAdd = PageDrawingWrapper()
                    drawingStateToAdd.listOfPaths = pageImage.paths.toMutableList()
                    drawingStateToAdd.listOfBrushes = pageImage.brushesforpaths.toMutableList() //need to make copy of list?
                    drawingStateToAdd.pageNumberForDraw = pageNumber

                    var NeedNewEntry = 1

                    for(entry in AllPageDrawings) {
                        if(entry?.pageNumberForDraw == pageNumber) {
                            NeedNewEntry = 0
                            entry.listOfPaths = pageImage.paths.toMutableList()
                            entry.listOfBrushes = pageImage.brushesforpaths.toMutableList()
                        }
                    }

                    if(NeedNewEntry == 1) AllPageDrawings.add(drawingStateToAdd)



                    var commandStateToAdd = AllCommandsWrapper()
                    commandStateToAdd.listOfCommands = pageImage.listOfCommands.toMutableList()
                    commandStateToAdd.listOfUndos = pageImage.listOfUndos.toMutableList() //need to make copy of list?
                    commandStateToAdd.pageNumberForCommand = pageNumber

                    NeedNewEntry = 1

                    for(entry in AllPageCommands) {
                        if(entry?.pageNumberForCommand == pageNumber) {
                            NeedNewEntry = 0
                            entry?.listOfCommands = pageImage.listOfCommands.toMutableList()
                            entry?.listOfUndos = pageImage.listOfUndos.toMutableList()
                        }
                    }

                    if(NeedNewEntry == 1) AllPageCommands.add(commandStateToAdd)

                    pageNumber--;
                    bottomBarView.setText("Page Number: " + (pageNumber+1) + "/" + totalpages);
                    try {
                        showPage(pageNumber)
                    } catch (exception: IOException) {
                        Log.d(LOGNAME, "Error opening PDF")
                    }


                    if(nextButtonObject.isClickable == false) {
                        nextButtonObject.setAlpha(1f);
                        nextButtonObject.setClickable(true);
                    }

                    if(pageNumber == 0) {
                        setAlpha(.5f);
                        setClickable(false);
                    }

                    pageImage.paths.clear()
                    pageImage.brushesforpaths.clear()

                    for(entry in AllPageDrawings) {
                        if(entry?.pageNumberForDraw == pageNumber) {
                            pageImage.paths = entry.listOfPaths
                            pageImage.brushesforpaths = entry.listOfBrushes
                        }
                    }

                    pageImage.listOfCommands.clear()
                    pageImage.listOfUndos.clear()

                    for(entry in AllPageCommands) {
                        if(entry?.pageNumberForCommand == pageNumber) {
                            pageImage.listOfCommands = entry.listOfCommands
                            pageImage.listOfUndos = entry.listOfUndos
                        }
                    }

                }
            }
        }

        nextButtonObject.apply {
            setOnClickListener {
                try {
                    Log.d("help", "help")
                    ObjectOutputStream(FileOutputStream("test")).use{ it -> it.writeObject(pageImage.paths[0])}
                } catch (e: Exception) {
                    Log.d("error", "with persistence")
                }

                if(pageNumber < totalpages-1)  {

                    pageImage.currentMatrix = Matrix()

                    var drawingStateToAdd = PageDrawingWrapper()
                    drawingStateToAdd.listOfPaths = pageImage.paths.toMutableList()
                    drawingStateToAdd.listOfBrushes = pageImage.brushesforpaths.toMutableList() //need to make copy of list?
                    drawingStateToAdd.pageNumberForDraw = pageNumber

                    var NeedNewEntry = 1

                    for(entry in AllPageDrawings) {
                        if(entry?.pageNumberForDraw == pageNumber) {
                            NeedNewEntry = 0
                            entry.listOfPaths = pageImage.paths.toMutableList()
                            entry.listOfBrushes = pageImage.brushesforpaths.toMutableList()
                        }
                    }

                    if(NeedNewEntry == 1) AllPageDrawings.add(drawingStateToAdd)


                    var commandStateToAdd = AllCommandsWrapper()
                    commandStateToAdd.listOfCommands = pageImage.listOfCommands.toMutableList()
                    commandStateToAdd.listOfUndos = pageImage.listOfUndos.toMutableList() //need to make copy of list?
                    commandStateToAdd.pageNumberForCommand = pageNumber

                    NeedNewEntry = 1

                    for(entry in AllPageCommands) {
                        if(entry?.pageNumberForCommand == pageNumber) {
                            NeedNewEntry = 0
                            entry?.listOfCommands = pageImage.listOfCommands.toMutableList()
                            entry?.listOfUndos = pageImage.listOfUndos.toMutableList()
                        }
                    }

                    if(NeedNewEntry == 1) AllPageCommands.add(commandStateToAdd)

                    pageNumber++;
                    bottomBarView.setText("Page Number: " + (pageNumber+1) + "/" + totalpages);
                    try {
                        showPage(pageNumber)
                    } catch (exception: IOException) {
                        Log.d(LOGNAME, "Error opening PDF")
                    }

                    if(pageNumber == totalpages - 1) {
                        setAlpha(.5f);
                        setClickable(false);
                    }

                    if(backButtonObject.isClickable == false) {
                        backButtonObject.setAlpha(1f);
                        backButtonObject.setClickable(true);
                    }

                    pageImage.paths.clear()
                    pageImage.brushesforpaths.clear()

                    for(entry in AllPageDrawings) {
                        if(entry?.pageNumberForDraw == pageNumber) {
                            pageImage.paths = entry.listOfPaths
                            pageImage.brushesforpaths = entry.listOfBrushes
                        }
                    }

                    pageImage.listOfCommands.clear()
                    pageImage.listOfUndos.clear()

                    for(entry in AllPageCommands) {
                        if(entry?.pageNumberForCommand == pageNumber) {
                            pageImage.listOfCommands = entry.listOfCommands
                            pageImage.listOfUndos = entry.listOfUndos
                        }
                    }

                }
            }
        }

        val moveButtonObject = findViewById<View>(R.id.moveButton) as Button
        val penButtonObject = findViewById<View>(R.id.penButton) as Button
        val highlightButtonObject = findViewById<View>(R.id.HighlighterButton) as Button
        val eraserButtonObject = findViewById<View>(R.id.EraserButton) as Button

        moveButtonObject.apply {
            setOnClickListener {
                pageImage.current_brush = -1;
                pageImage.multiTouchMove = true;
            }
        }

        penButtonObject.apply {
            setOnClickListener {
                pageImage.current_brush = 0;
                pageImage.multiTouchMove = false;
            }
        }

        highlightButtonObject.apply {
            setOnClickListener {
                pageImage.current_brush = 1;
                pageImage.multiTouchMove = false;
            }
        }

        eraserButtonObject.apply {
            setOnClickListener {
                pageImage.current_brush = 2;
                pageImage.multiTouchMove = false;
            }
        }

        undoButtonObject = findViewById<View>(R.id.undoButton) as Button
        redoButtonObject = findViewById<View>(R.id.redoButton) as Button


        fun checkgreyedoutbuttons() {
            if(pageImage.listOfCommands.isEmpty()) {
                undoButtonObject.setAlpha(.5f);
                undoButtonObject.setClickable(false);
            } else {
                undoButtonObject.setAlpha(1f);
                undoButtonObject.setClickable(true);
            }

            if(pageImage.listOfUndos.isEmpty()) {
                redoButtonObject.setAlpha(.5f);
                redoButtonObject.setClickable(false);
            } else {
                redoButtonObject.setAlpha(1f);
                redoButtonObject.setClickable(true);
            }
        }

        undoButtonObject.apply {
            setOnClickListener {
                try {
                    if(!pageImage.listOfCommands.isEmpty() && pageImage.listOfCommands.last()?.Erased == false) {
                        pageImage.paths.removeLast()
                        pageImage.brushesforpaths.removeLast()

                        pageImage.listOfUndos.add(pageImage.listOfCommands.last())
                        pageImage.listOfCommands.removeLast()

                        checkgreyedoutbuttons()
                    } else if (!pageImage.listOfCommands.isEmpty() && pageImage.listOfCommands.last()?.Erased == true) {
                        pageImage.paths.add(pageImage.listOfCommands.last()?.thePath)
                        pageImage.brushesforpaths.add(pageImage.listOfCommands.last()?.theBrush)

                        pageImage.listOfUndos.add(pageImage.listOfCommands.last())
                        pageImage.listOfCommands.removeLast()

                        checkgreyedoutbuttons()
                    }

                    checkgreyedoutbuttons()
                }  catch (exception: IOException) {

                }
            }
        }


        redoButtonObject.apply {
            setOnClickListener {
                try {
                    if(!pageImage.listOfUndos.isEmpty() && pageImage.listOfUndos.last()?.Erased == false) {
                        pageImage.paths.add(pageImage.listOfUndos.last()?.thePath)
                        pageImage.brushesforpaths.add(pageImage.listOfUndos.last()?.theBrush)

                        pageImage.listOfCommands.add(pageImage.listOfUndos.last())
                        pageImage.listOfUndos.removeLast()

                        checkgreyedoutbuttons()
                    } else if(!pageImage.listOfUndos.isEmpty() && pageImage.listOfUndos.last()?.Erased == true) {
                        pageImage.paths.remove(pageImage.listOfUndos.last()?.thePath)
                        pageImage.brushesforpaths.remove(pageImage.listOfUndos.last()?.theBrush)

                        pageImage.listOfCommands.add(pageImage.listOfUndos.last())
                        pageImage.listOfUndos.removeLast()

                        checkgreyedoutbuttons()
                    }

                    checkgreyedoutbuttons()

                } catch (ex: IOException) {

                }
            }
        }

        // open page 0 of the PDF
        // it will be displayed as an image in the pageImage (above)
        try {
            openRenderer(this)
            showPage(pageNumber)
            backButtonObject.setAlpha(.5f);
            backButtonObject.setClickable(false);

            undoButtonObject.setAlpha(.5f);
            undoButtonObject.setClickable(false);
            redoButtonObject.setAlpha(.5f);
            redoButtonObject.setClickable(false);
            bottomBarView.setText("Page Number: " + (pageNumber+1) + "/" + totalpages);

        } catch (exception: IOException) {
            Log.d(LOGNAME, "Error opening PDF")
        }
    }


    override fun onStop() {
        super.onStop()
        try {
            closeRenderer()
        } catch (ex: IOException) {
            Log.d(LOGNAME, "Unable to close PDF renderer")
        }
    }

    @Throws(IOException::class)
    private fun openRenderer(context: Context) {
        // In this sample, we read a PDF from the assets directory.
        val file = File(context.cacheDir, FILENAME)
        if (!file.exists()) {
            // pdfRenderer cannot handle the resource directly,
            // so extract it into the local cache directory.
            val asset = this.resources.openRawResource(FILERESID)
            val output = FileOutputStream(file)
            val buffer = ByteArray(1024)
            var size: Int
            while (asset.read(buffer).also { size = it } != -1) {
                output.write(buffer, 0, size)
            }
            asset.close()
            output.close()
        }
        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

        // capture PDF data
        // all this just to get a handle to the actual PDF representation
        pdfRenderer = PdfRenderer(parcelFileDescriptor)

        totalpages = pdfRenderer.getPageCount()


    }

    // do this before you quit!
    @Throws(IOException::class)
    private fun closeRenderer() {
        currentPage?.close()
        pdfRenderer.close()
        parcelFileDescriptor.close()
    }

    private fun showPage(index: Int) {
        if (pdfRenderer.pageCount <= index) {
            return
        }
        // Close the current page before opening another one.
        currentPage?.close()

        // Use `openPage` to open a specific page in PDF.
        currentPage = pdfRenderer.openPage(index)

        if (currentPage != null) {
            // Important: the destination bitmap must be ARGB (not RGB).
            val bitmap = Bitmap.createBitmap(currentPage!!.getWidth(), currentPage!!.getHeight(), Bitmap.Config.ARGB_8888)

            // Here, we render the page onto the Bitmap.
            // To render a portion of the page, use the second and third parameter. Pass nulls to get the default result.
            // Pass either RENDER_MODE_FOR_DISPLAY or RENDER_MODE_FOR_PRINT for the last parameter.
            currentPage!!.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            // Display the page
            pageImage.setImage(bitmap)
        }
    }



}