import axios from 'axios';

const API_URL = '/api';

export const OrderService = {
  // Get all orders
  getAllOrders: async () => {
    try {
      const response = await axios.get(`${API_URL}/orders`);
      return response.data;
    } catch (error) {
      console.error('Error fetching all orders:', error);
      throw error;
    }
  },

  // Get a specific order status
  getOrderStatus: async (orderId) => {
    try {
      const response = await axios.get(`${API_URL}/orders/${orderId}`);
      return response.data;
    } catch (error) {
      console.error(`Error fetching order ${orderId}:`, error);
      throw error;
    }
  },

  // Get the quote for an order
  getQuote: async (orderId) => {
    try {
      const response = await axios.get(`${API_URL}/orders/${orderId}/quote`);
      return response.data;
    } catch (error) {
      console.error(`Error fetching quote for order ${orderId}:`, error);
      throw error;
    }
  },

  // Submit a new order
  submitOrder: async (orderData) => {
    try {
      const response = await axios.post(`${API_URL}/orders`, orderData);
      return response.data;
    } catch (error) {
      console.error('Error submitting order:', error);
      throw error;
    }
  },

  // Accept a quote
  acceptQuote: async (orderId) => {
    try {
      const response = await axios.post(`${API_URL}/orders/${orderId}/accept`);
      return response.data;
    } catch (error) {
      console.error(`Error accepting quote for order ${orderId}:`, error);
      // Add detailed error information
      if (error.response) {
        console.error(`Server returned error ${error.response.status}: ${JSON.stringify(error.response.data)}`);
      } else if (error.request) {
        console.error('No response received from server. Network issue?');
      }
      throw error;
    }
  },

  // Reject a quote
  rejectQuote: async (orderId) => {
    try {
      const response = await axios.post(`${API_URL}/orders/${orderId}/reject`);
      return response.data;
    } catch (error) {
      console.error(`Error rejecting quote for order ${orderId}:`, error);
      // Add detailed error information
      if (error.response) {
        console.error(`Server returned error ${error.response.status}: ${JSON.stringify(error.response.data)}`);
      } else if (error.request) {
        console.error('No response received from server. Network issue?');
      }
      throw error;
    }
  }
};
