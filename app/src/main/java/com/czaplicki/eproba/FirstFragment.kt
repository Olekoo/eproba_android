package com.czaplicki.eproba

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.RoomDatabase
import com.czaplicki.eproba.api.EprobaApi
import com.czaplicki.eproba.api.EprobaService
import com.czaplicki.eproba.databinding.FragmentFirstBinding
import com.google.android.material.snackbar.Snackbar
import net.openid.appauth.AuthorizationService


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    private lateinit var mAuthStateManager: AuthStateManager
    private lateinit var authService: AuthorizationService
    private var recyclerView: RecyclerView? = null
    private val mSwipeRefreshLayout by lazy { _binding!!.swipeRefreshLayout }
    private val api: EprobaApi = EprobaApi()
    var examList: MutableList<Exam> = mutableListOf()
    private val binding get() = _binding!!
    private val userDao: UserDao by lazy { (activity?.application as EprobaApplication).database.userDao() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        mAuthStateManager = AuthStateManager.getInstance(requireContext())
        authService = AuthorizationService(requireContext())
        recyclerView = binding.recyclerView
        recyclerView?.layoutManager = LinearLayoutManager(view?.context)
        recyclerView?.adapter = ExamAdapter(examList)
        mSwipeRefreshLayout.setOnRefreshListener {
            updateExams()
            getUsers()
            val users: List<User> = userDao.getAll()
            Log.d("users", users.toString())

        }
        recyclerView!!.setOnScrollChangeListener { _, _, _, _, oldY ->
            if (oldY >= 40 || oldY == 0 || !recyclerView!!.canScrollVertically(-1)) {
                (activity as? MainActivity)?.fab?.extend()
            } else if (oldY < -40) {
                (activity as? MainActivity)?.fab?.shrink()
            }
        }

        return binding.root

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        updateExams()
    }

    private fun updateExams() {
        mAuthStateManager.current.performActionWithFreshTokens(
            authService
        ) { accessToken, _, _ ->
            if (accessToken == null) {
                recyclerView?.visibility = View.GONE
                binding.notLoggedIn.visibility = View.VISIBLE
                binding.loginButton.setOnClickListener {
                    (activity as? MainActivity)?.startAuth()
                }
                return@performActionWithFreshTokens
            } else if (recyclerView?.visibility == View.GONE) {
                recyclerView?.visibility = View.VISIBLE
                binding.notLoggedIn.visibility = View.GONE
            }
            mAuthStateManager.updateSavedState()
            mSwipeRefreshLayout.isRefreshing = true
            api.getRetrofitInstance(requireContext(), accessToken)!!
                .create(EprobaService::class.java).getUserExams()
                .enqueue(object : retrofit2.Callback<List<Exam>> {
                    override fun onFailure(call: retrofit2.Call<List<Exam>>, t: Throwable) {
                        Snackbar.make(
                            binding.root,
                            "Błąd połączenia z serwerem",
                            Snackbar.LENGTH_LONG
                        ).show()
                        t.message?.let { Log.e("FirstFragment", it) }
                        mSwipeRefreshLayout.isRefreshing = false
                    }

                    override fun onResponse(
                        call: retrofit2.Call<List<Exam>>,
                        response: retrofit2.Response<List<Exam>>
                    ) {
                        if (response.body() != null) {
                            examList.clear()
                            examList.addAll(response.body()!!)
                            if (PreferenceManager.getDefaultSharedPreferences(requireContext())
                                    .getBoolean("ads", true)
                            ) examList.add(Exam(id = -1, name = "ad"))
                        } else {
                            Snackbar.make(
                                binding.root,
                                "Błąd połączenia z serwerem",
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                        recyclerView?.adapter?.notifyDataSetChanged()
//                        recyclerView?.adapter = ExamAdapter(requireContext(), examList)
                        mSwipeRefreshLayout.isRefreshing = false
                    }
                })
        }
    }

    fun getUsers() {
        mAuthStateManager.current.performActionWithFreshTokens(
            authService
        ) { accessToken, _, _ ->
            if (accessToken == null) {
                recyclerView?.visibility = View.GONE
                binding.notLoggedIn.visibility = View.VISIBLE
                binding.loginButton.setOnClickListener {
                    (activity as? MainActivity)?.startAuth()
                }
                return@performActionWithFreshTokens
            } else if (recyclerView?.visibility == View.GONE) {
                recyclerView?.visibility = View.VISIBLE
                binding.notLoggedIn.visibility = View.GONE
            }
            mAuthStateManager.updateSavedState()
            api.getRetrofitInstance(requireContext(), accessToken)!!
                .create(EprobaService::class.java).getUsersPublicInfo()
                .enqueue(object : retrofit2.Callback<List<User>> {
                    override fun onFailure(call: retrofit2.Call<List<User>>, t: Throwable) {
                        Snackbar.make(
                            binding.root,
                            "Błąd połączenia z serwerem",
                            Snackbar.LENGTH_LONG
                        ).show()
                        t.message?.let { Log.e("FirstFragment", it) }
                    }

                    override fun onResponse(
                        call: retrofit2.Call<List<User>>,
                        response: retrofit2.Response<List<User>>
                    ) {
                        if (response.body() != null) {
                            val users = response.body()!!
                            userDao.insertUsers(*users.toTypedArray())
                        } else {
                            Snackbar.make(
                                binding.root,
                                "Błąd połączenia z serwerem",
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                })
        }
    }

}