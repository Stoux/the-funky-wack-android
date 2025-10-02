package nl.stoux.tfw.automotive.di

import dagger.assisted.AssistedFactory
import nl.stoux.tfw.automotive.car.TfwCarSession

@AssistedFactory
interface TfwCarSessionFactory {
    fun create(): TfwCarSession
}