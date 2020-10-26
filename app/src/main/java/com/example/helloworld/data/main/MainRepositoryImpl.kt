package com.example.helloworld.data.main

import com.example.helloworld.R
import com.example.helloworld.core.data.localdb.AppDatabase
import com.example.helloworld.core.data.network.RetrofitException
import com.example.helloworld.data.datasources.networksource.MessageNetworkSource
import com.example.helloworld.core.data.preference.AppPreference
import com.example.helloworld.data.main.model.Message
import io.reactivex.Maybe
import io.reactivex.Single
import retrofit2.Response
import java.lang.Exception
import java.util.logging.Logger
import javax.inject.Inject

class MainRepositoryImpl @Inject constructor(private val networkSource: MessageNetworkSource,
                                             private val db: AppDatabase,
                                             private val preference: AppPreference) :
    MainRepository {

    /**
     *   fetch message from network
     *       if no internet connection error occurred, then
     *           fetch message from local db
     *           if any error occurs while fetching data from local db, than
     *               get message from preference
     *   on message retrieved successfully save message into preference and local db
     */

    override fun getMessage(): Maybe<Message> {
        return networkSource.getMessage()
            .onErrorResumeNext { throwable: Throwable ->
                if(throwable is RetrofitException) {
                    if(throwable.getKind() == RetrofitException.Kind.NETWORK) {
                        db.messageDao().getMessage()
                            .onErrorResumeNext { _ ->
                                Single.create<Message> {
                                    it.onSuccess(Message(message = preference.message))
                                }
                            }
                            .flatMapMaybe {
                                Maybe.create<Message>{ emitter ->
                                    emitter.onSuccess(it) }
                            }
                    } else Maybe.error(throwable)
                } else Maybe.error(throwable)
            }.map {
                var message = it
                if(it.message.isNullOrEmpty()) {
                    message = Message(message = "Hello World!")
                }
                preference.message = message.message
                db.messageDao().insertMessage(message)
                message
            }
    }

    override suspend fun getCoroutineMessage(): Message {

        var message = Message()

        runCatching {
            networkSource.getCoroutineMessage().body()!!
        }.onSuccess {
            message = it
        }.onFailure {
            //todo get data from the local db
            message = Message(message = preference.message)
        }

        if(message.message.isEmpty())
           message = Message(message = "Hello World!")

        preference.message = message.message
        db.messageDao().insertMessage(message)

        return message
    }
}