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
	 */
	public int getCUDAcomputeCapabilityMinorVersion(int devCUDA);
	public int getCUDAcomputeCapabilityMajorVersion(int devCUDA);
	public int getNumDevicesCUDA();
	public void getNameDeviceCUDA(int devCUDA, byte[] name);
	public long getMemDeviceCUDA(int devCUDA);
}
