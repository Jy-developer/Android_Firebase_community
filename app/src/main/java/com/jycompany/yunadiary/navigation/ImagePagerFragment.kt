package com.jycompany.yunadiary.navigation

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.jycompany.yunadiary.R
import kotlinx.android.synthetic.main.fragment_image_pager.view.*

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [ImagePagerFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class ImagePagerFragment : Fragment() {
    val TAG = "ImagePagerFrag_TAG"
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
        var view = inflater.inflate(R.layout.fragment_image_pager, container, false) as View
        Glide.with(view.context)
            .load(imageUrl)
            .thumbnail(0.5f)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .disallowHardwareConfig()
            .error(R.drawable.com_facebook_close)
            .into(view.image_fragment_view)
//        Picasso.get()
//                .load(imageUrl)
//                .error(R.drawable.com_facebook_close)
//                .into(view.image_fragment_view)

        //멀티 사진 업로드하려고 할 때 보이는 Fragment이므로 클릭리스너에 별도 설정 안함
//        view.image_fragment_view.setOnClickListener {}

        return view
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment ImagePagerFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            ImagePagerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}