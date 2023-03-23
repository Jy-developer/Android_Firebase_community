package com.jycompany.yunadiary.navigation

import android.content.Intent
import android.graphics.Point
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.jycompany.yunadiary.MainActivity
import com.jycompany.yunadiary.R
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.fragment_picasso_image_pager.view.*

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [PicassoImagePagerFrag.newInstance] factory method to
 * create an instance of this fragment.
 */
class PicassoImagePagerFrag : Fragment() {         //DetailViewFragment에서 보여지는 viewPager 용도..였으나 그냥 ViewPager2 써서 사실상 안 쓰는 클래스 됨
                                            //그래도 xml 은 ViewPager2 에서 쓰고 있으니 지우지 말 것
    var imageUrl : String? = null
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            imageUrl = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        var view = inflater.inflate(R.layout.fragment_picasso_image_pager, container, false)
        Log.d("DetailView_tag", "PicassoImageFrag 생성됨")

        val display = activity!!.windowManager.defaultDisplay
        val size = Point()
        display.getRealSize(size)

        Picasso.get()
            .load(imageUrl)
            .resize(size.x, size.y)
            .error(R.drawable.com_facebook_close)
            .into(view.picasso_image_fragment_view)

        view.picasso_image_fragment_view.setOnClickListener {
            var viewIntent = Intent(context as MainActivity, ShowPictureActivity2::class.java)
            viewIntent.putExtra("imageUri", imageUrl)
            startActivity(viewIntent)
        }
        return view
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment PicassoImagePagerFrag.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            PicassoImagePagerFrag().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}