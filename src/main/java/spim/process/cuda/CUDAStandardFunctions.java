/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2007 - 2022 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */
package spim.process.cuda;

import com.sun.jna.Library;

public interface CUDAStandardFunctions extends Library
{
	/*
	__declspec(dllexport) int getCUDAcomputeCapabilityMinorVersion(int devCUDA);
	__declspec(dllexport) int getCUDAcomputeCapabilityMajorVersion(int devCUDA);
	__declspec(dllexport) int getNumDevicesCUDA();
	__declspec(dllexport) char* getNameDeviceCUDA(int devCUDA);
	__declspec(dllexport) long long int getMemDeviceCUDA(int devCUDA);
	long long int getFreeMemDeviceCUDA(int devCUDA)
	 */
	public int getCUDAcomputeCapabilityMinorVersion(int devCUDA);
	public int getCUDAcomputeCapabilityMajorVersion(int devCUDA);
	/**
	 * @return -1 if driver crashed, otherwise the number of CUDA devices, &lt;= 0
	 */
	public int getNumDevicesCUDA();
	public void getNameDeviceCUDA(int devCUDA, byte[] name);
	public long getMemDeviceCUDA(int devCUDA);
	public long getFreeMemDeviceCUDA(int devCUDA);
}
