package com.jycompany.yunadiary.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ImageDisplayView: View, View.OnTouchListener {
    lateinit var mContext : Context
    var mCanvas : Canvas? = null
    var mBitmap : Bitmap? = null
    var mPaint : Paint? = null

    var lastX : Int? = null
    var lastY : Int? = null

    var sourceBitmap : Bitmap? = null
    var mMatrix : Matrix? = null

    var sourceWidth : Float = 0.0f
    var sourceHeight : Float = 0.0f

    var bitmapCenterX : Float? = null
    var bitmapCenterY : Float? = null

    var scaleRatio : Float? = null
    var totalScaleRatio : Float? = null

    var displayWidth : Float = 0.0f
    var displayHeight : Float = 0.0f

    var displayCenterX : Int = 0
    var displayCenterY : Int = 0

    public var startX : Float? = null
    public var startY : Float? = null

    companion object{
        public val MAX_SCALE_RATIO : Float = 5.0f
        public val MIN_SCALE_RATIO : Float = 0.1f
    }

    var oldDistance : Float = 0.0f
    var oldPointerCount : Int = 0
    var isScrolling : Boolean = false
    var distanceThreshold : Float = 1.0f

    constructor(context: Context) : super(context){
        mContext = context
        init2()
    }

    constructor(context: Context, attrs : AttributeSet) : super(context, attrs){
        mContext = context
        init2()
    }

    fun init2(){
        mPaint = Paint()
        mMatrix = Matrix()

        lastX = -1
        lastY = -1

        setOnTouchListener(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if(w > 0 && h > 0){
            newImage(w,h)
            redraw()
        }
    }

    fun newImage(width : Int, height : Int){
        var img : Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        var canvas : Canvas = Canvas()
        canvas.setBitmap(img)

        mBitmap = img
        mCanvas = canvas

        displayWidth = width.toFloat()
        displayHeight = height.toFloat()

        displayCenterX = width/2
        displayCenterY = height/2
    }

    fun drawBackground(canvas : Canvas){
        canvas.drawColor(Color.BLACK)
    }

    override fun onDraw(canvas: Canvas?) {
        if(mBitmap != null){
            canvas?.drawBitmap(mBitmap!!, 0.0f, 0.0f, null)
        }
    }

    public fun setImageData(image : Bitmap){
        recycle()

        sourceBitmap = image
        sourceWidth = sourceBitmap!!.width.toFloat()
        sourceHeight = sourceBitmap!!.height.toFloat()

        bitmapCenterX = sourceBitmap!!.width.toFloat()/2
        bitmapCenterY = sourceBitmap!!.height.toFloat()/2

        scaleRatio = 1.0f
        totalScaleRatio = 1.0f
    }

    fun recycle(){
        sourceBitmap?.recycle()
    }

    public fun redraw(){
        if(sourceBitmap == null)return

        drawBackground(mCanvas!!)
        var originX : Float = (displayWidth - sourceBitmap!!.width.toFloat()) / 2.0f
        var originY : Float = (displayHeight - sourceBitmap!!.height.toFloat()) / 2.0f

        mCanvas!!.translate(originX, originY)
        mCanvas!!.drawBitmap(sourceBitmap!!, mMatrix!!, mPaint)
        mCanvas!!.translate(-originX, -originY)

        invalidate()
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        val action : Int = event!!.action

        var pointerCount : Int = event.pointerCount
        when(action){
            MotionEvent.ACTION_DOWN ->{
                if(pointerCount == 1){
                    var curX : Float = event.x
                    var curY : Float = event.y

                    startX = curX
                    startY = curY

                }else if (pointerCount == 2){
                    oldDistance = 0.0f
                    isScrolling = true
                }
                return true
            }
            MotionEvent.ACTION_MOVE ->{
                if(pointerCount == 1){
                    if(isScrolling){
                        return true
                    }
                    var curX : Float = event.x
                    var curY : Float = event.y
                    if(startX == 0.0f){
                        startX = curX
                        startY = curY
                        return true
                    }
                    var offsetX : Float = startX!!.minus(curX)
                    var offsetY : Float = startY!!.minus(curY)

                    if(oldPointerCount == 2){
                    }else{
//                        if(totalScaleRatio!! > 1.0f){
//                            moveImage(-offsetX, -offsetY)
//                        }     //1배 이상일때만 moveImage할 필요없으니
                        moveImage(-offsetX, -offsetY)
                        startX = curX
                        startY = curY
                    }
                }else if(pointerCount == 2){
                    var x1 : Float = event.getX(0)
                    var y1 : Float = event.getY(0)
                    var x2 : Float = event.getX(1)
                    var y2 : Float = event.getY(1)

                    var dx : Float = x1-x2
                    var dy : Float = y1-y2
                    var distance : Float = Math.sqrt((dx*dy+dy*dy).toDouble()).toFloat()

                    var outScaleRatio : Float = 0.0f
                    if(oldDistance == 0.0f){
                        oldDistance = distance
                    }
                    if(distance > oldDistance){
                        if((distance-oldDistance)<distanceThreshold){
                            return true
                        }
                        outScaleRatio = scaleRatio!! + (oldDistance / distance * 0.05f)
                    }else if(distance < oldDistance){
                        if((oldDistance-distance)<distanceThreshold){
                            return true
                        }
                        outScaleRatio = scaleRatio!! - (distance / oldDistance * 0.05f)
                    }
                    //아래 if가 outScaleRatio를 기준으로 할게 아니라, totalScaleRatio를 기준으로 해야 할듯
                    if(outScaleRatio!! < MIN_SCALE_RATIO || outScaleRatio!! > MAX_SCALE_RATIO){
                        //do nothing.
                    }else{
                        scaleImage(outScaleRatio)
                    }
                    oldDistance = distance
                }
                oldPointerCount = pointerCount
            }
            MotionEvent.ACTION_UP->{
                if(pointerCount == 1){
                    var curX : Float = event.x
                    var curY : Float = event.y

                    var offsetX : Float = startX!!.minus(curX)
                    var offsetY : Float = startY!!.minus(curY)

                    if(oldPointerCount == 2){
                    }else{
                        moveImage(-offsetX, -offsetY)
                    }
                }else{
                    isScrolling = false
                }
                return true
            }

        }
        return true
    }

    private fun scaleImage(inScaleRatio : Float){
        mMatrix?.postScale(inScaleRatio, inScaleRatio, bitmapCenterX!!, bitmapCenterY!!)
        mMatrix?.postRotate(0.0f)

        totalScaleRatio = totalScaleRatio?.times(inScaleRatio)

        redraw()
    }

    private fun moveImage(offsetX : Float, offsetY : Float){
        mMatrix?.postTranslate(offsetX, offsetY)
        redraw()
    }
}